import puppeteer from 'puppeteer-core'
const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new', args: ['--no-sandbox']
})
const page = await browser.newPage()
await page.setViewport({ width: 1600, height: 900 })
await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 1500))
// 登录
await page.evaluate(() => {
  localStorage.setItem('edu_token', 'x')
  localStorage.setItem('edu_user', JSON.stringify({ id: 1, userType: 1, realName: 'A', username: 'admin' }))
  localStorage.setItem('edu_menus', JSON.stringify([
    { id: 4, parentId: 0, name: '首页概览', path: '/home', component: 'home/Index', icon: 'Home', type: 'C' },
    { id: 2, parentId: 0, name: '教务管理', path: '/edu', type: 'M', children: [
      { id: 201, parentId: 2, name: '学生管理', path: 'student', component: 'edu/Student', type: 'C' }
    ] }
  ]))
})
await page.goto('http://localhost:5173/home', { waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 2500))
const info = await page.evaluate(() => {
  const doc = document.documentElement
  const table = document.querySelector('.el-table')
  return {
    hasHScroll: doc.scrollWidth > doc.clientWidth,
    hasVScroll: doc.scrollHeight > doc.clientHeight,
    tableFontSize: table ? getComputedStyle(table).fontSize : null,
    headerLetterSpacing: table ? getComputedStyle(table.querySelector('th .cell') || document.body).letterSpacing : null
  }
})
console.log(JSON.stringify(info))
await page.screenshot({ path: 'list-shot.png' })
await browser.close()
