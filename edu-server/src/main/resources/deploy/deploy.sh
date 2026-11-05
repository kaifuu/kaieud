#!/bin/bash
# 一键部署(在解包目录内执行): bash deploy.sh
# 拉起 kaied-app(后端) + kaied-web(nginx前端)；随包模式额外拉起 kaied-db(MySQL8+初始SQL)
# 外部数据库模式: 自动建库, 空库时导入初始SQL(已有表则跳过, 不覆盖数据)
set -e
cd "$(dirname "$0")"
[ -f .env ] || { echo '[ERROR] .env 缺失'; exit 1; }

getenv() { grep -E "^$1=" .env | head -1 | cut -d= -f2 | tr -d '\r'; }

APP_PORT=$(getenv APP_PORT)
DB_MODE=$(getenv DB_MODE)

if docker compose version >/dev/null 2>&1; then DC="docker compose";
elif command -v docker-compose >/dev/null 2>&1; then DC="docker-compose";
else echo '[ERROR] 未安装 docker compose,请先安装 Docker'; exit 1; fi

# 镜像拉取: 本地已有 -> 直连 -> 国内加速源兜底
pull() {
  IMG="$1"
  docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | grep -qx "$IMG" && return 0
  docker pull "$IMG" >/dev/null 2>&1 && return 0
  P="$IMG"; case "$P" in */*) : ;; *) P="library/$IMG" ;; esac
  for M in docker.m.daocloud.io docker.1ms.run hub.rat.dev; do
    if docker pull "$M/$P" >/dev/null 2>&1; then
      docker tag "$M/$P" "$IMG" && docker rmi "$M/$P" >/dev/null 2>&1 || true
      echo "  镜像 $IMG 经 $M 加速拉取"
      return 0
    fi
  done
  return 1
}
pull nginx:alpine || echo "  [WARN] nginx:alpine 拉取失败,若宿主机已有镜像可继续"

# 选择 compose 文件: 外部库不带 kaied-db
if [ "$DB_MODE" = 'EXTERNAL' ]; then
  cp -f compose-external.yml docker-compose.yml
  DB_HOST=$(getenv DB_HOST)
  DB_PORT=$(getenv DB_PORT)
  DB_NAME=$(getenv DB_NAME)
  DB_USER=$(getenv DB_USER)
  DB_PASS=$(getenv DB_PASS)
  echo "== 外部数据库初始化($DB_HOST:$DB_PORT/$DB_NAME) =="
  pull mysql:8 || echo "  [WARN] mysql:8 拉取失败,无法自动导库,请手工导入 sql/init.sql"
  docker run --rm -e MYSQL_PWD="$DB_PASS" mysql:8 mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
    --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci" \
    && echo '  数据库已就绪'
  CNT=$(docker run --rm -e MYSQL_PWD="$DB_PASS" mysql:8 mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
    --default-character-set=utf8mb4 -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='sys_config'" 2>/dev/null | tr -d '[:space:]')
  if [ "$CNT" = "0" ]; then
    echo '  空库: 导入初始数据(表结构+种子数据,约1分钟)...'
    docker run -i --rm -e MYSQL_PWD="$DB_PASS" mysql:8 mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
      --default-character-set=utf8mb4 "$DB_NAME" < ./sql/init.sql \
      && echo '  初始数据导入完成'
  else
    echo "  库中已有表($CNT),跳过导入(不覆盖线上数据)"
  fi
else
  cp -f compose-bundled.yml docker-compose.yml
  pull mysql:8 || echo "  [WARN] mysql:8 拉取失败,若宿主机已有镜像可继续"
fi

# nginx 配置按应用端口渲染
[ -f nginx.conf.tpl ] && sed "s/__APP_PORT__/${APP_PORT}/g" nginx.conf.tpl > nginx.conf

echo '== 停止旧容器(如有) =='
$DC down 2>/dev/null || true

echo '== 构建并拉起容器(首次含镜像拉取,请耐心) =='
$DC up -d --build

sleep 6
echo '== 容器状态 =='
$DC ps
echo '__DEPLOY_DONE__'
