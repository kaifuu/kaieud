import puppeteer from 'puppeteer-core'
import fs from 'fs'
const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new', args: ['--no-sandbox'], protocolTimeout: 60000
})
const page = await browser.newPage()
await page.setViewport({ width: 1600, height: 900 })
await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
await page.evaluate((t, m) => {
  localStorage.setItem('edu_token', t)
  localStorage.setItem('edu_user', JSON.stringify({ id: 1, userType: 1, realName: 'T', username: 'admin' }))
  localStorage.setItem('edu_menus', JSON.stringify([
    { id: 1, parentId: 0, name: 'X', path: '/system', type: 'M', children: [
      { id: 103, parentId: 1, name: 'U', path: 'user', component: 'system/User', type: 'C' }
    ] }
  ]))
}, fs.readFileSync('_tk.txt', 'utf8').trim(), fs.readFileSync('_menus.json', 'utf8'))
await page.goto('http://localhost:5173/user', { waitUntil: 'domcontentloaded' })
await new Promise((r) => setTimeout(r, 3000))
await page.evaluate(() => {
  const btn = [...document.querySelectorAll('.el-button')].find((b) => b.textContent.includes('新增用户'))
  btn?.click()
})
await new Promise((r) => setTimeout(r, 1500))
const out = await page.evaluate(() => ({
  drawerOpen: !!document.querySelector('.el-drawer'),
  drawerTitle: document.querySelector('.el-drawer__header')?.textContent?.trim() || '',
  uploadArea: !!document.querySelector('.el-drawer .upload-area'),
  dashed: (() => {
    const a = document.querySelector('.el-drawer .upload-area')
    return a ? getComputedStyle(a).borderTopStyle : null
  })(),
  plusText: document.querySelector('.el-drawer .plus-text')?.textContent || '',
  elUpload: !!document.querySelector('.el-drawer .el-upload')
}))
console.log(JSON.stringify(out))
await page.screenshot({ path: 'drawer-check.png' })
await browser.close()
