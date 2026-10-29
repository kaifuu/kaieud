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
}, t => t, {})
