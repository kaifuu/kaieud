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
  localStorage.setItem('edu_menus', m)
}, fs.readFileSync('_tk.txt', 'utf8').trim(), fs.readFileSync('_menus.json', 'utf8'))
await page.goto('http://localhost:5173/schedule', { waitUntil: 'domcontentloaded' })
await new Promise((r) => setTimeout(r, 3500))
for (const [tab, file] of [['周视图', 'shot-week.png'], ['日视图', 'shot-day.png'], ['月视图', 'shot-month.png']]) {
  await page.evaluate((tabName) => {
    const t = [...document.querySelectorAll('.view-tabs .el-tabs__item')].find((x) => x.textContent.trim() === tabName)
    t?.click()
  }, tab)
  await new Promise((r) => setTimeout(r, 1500))
  await page.screenshot({ path: file })
  const ev = await page.evaluate(() => document.querySelectorAll('.fc-event').length)
  console.log(tab, 'events:', ev)
}
await browser.close()
