import puppeteer from 'puppeteer-core'
import fs from 'fs'
const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new', args: ['--no-sandbox'], protocolTimeout: 60000
})
const page = await browser.newPage()
await page.setViewport({ width: 1600, height: 900 })
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 150)))
await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
await page.evaluate((t, m) => {
  localStorage.setItem('edu_token', t)
  localStorage.setItem('edu_user', JSON.stringify({ id: 1, userType: 1, realName: 'T', username: 'admin' }))
  localStorage.setItem('edu_menus', m)
}, fs.readFileSync('_tk.txt', 'utf8').trim(), fs.readFileSync('_menus.json', 'utf8'))
await page.goto('http://localhost:5173/schedule', { waitUntil: 'domcontentloaded' })
await new Promise((r) => setTimeout(r, 4000))
const out = await page.evaluate(() => {
  const tabs = [...document.querySelectorAll('.view-tabs .el-tabs__item')].map((t) => ({
    name: t.textContent.trim(), active: t.classList.contains('is-active')
  }))
  const calPanel = document.querySelector('.cal-panel')
  const fc = document.querySelector('.fc')
  const visibleFc = fc && fc.offsetHeight > 0
  return {
    tabs,
    calPanelShown: calPanel ? getComputedStyle(calPanel).display !== 'none' : false,
    calH: fc ? Math.round(fc.getBoundingClientRect().height) : null,
    fcMounted: !!fc,
    visibleFc
  }
})
console.log(JSON.stringify(out, null, 2))
console.log('ERRORS:', errs.length ? errs : 'none')
await page.screenshot({ path: 'sch-now2.png' })
await browser.close()
