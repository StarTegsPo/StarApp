// Сборка курса: node build.cjs → ../course.pdf и ../cover.jpg (нужен playwright)
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto('file://' + path.join(__dirname, 'course.html'));
  await p.pdf({ path: path.join(__dirname, '..', 'course.pdf'), format: 'A4', printBackground: true, preferCSSPageSize: true,
    displayHeaderFooter: true, headerTemplate: '<span></span>',
    footerTemplate: '<div style="width:100%;font-size:8px;color:#a7a1bf;padding:0 15mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif"><span>Нейросети на практике</span><span class="pageNumber"></span></div>' });
  const c = await b.newPage({ viewport: { width: 1280, height: 720 } });
  await c.goto('file://' + path.join(__dirname, 'cover.html'));
  await c.screenshot({ path: path.join(__dirname, '..', 'cover.jpg'), type: 'jpeg', quality: 90 });
  await b.close();
})();
