// Renders SVG files to PNG at the wallpaper size (1080 x 2337) with Chromium.
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1080 / 390 });
  for (const f of process.argv.slice(2)) {
    const svg = fs.readFileSync(f, 'utf8');
    await page.setContent(`<html><body style="margin:0;background:#000">${svg}</body></html>`);
    await page.screenshot({ path: f.replace(/\.svg$/, '.png'), clip: { x: 0, y: 0, width: 390, height: 844 } });
  }
  await browser.close();
})();
