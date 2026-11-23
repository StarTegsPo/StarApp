// node render.cjs [ids...] [--preview]   → out/NN.mp4 (1080x1920, 30fps, со звуком)
const { chromium } = require('playwright');
const { spawn, execFileSync } = require('child_process');
const fs = require('fs'), path = require('path');
const DIR = __dirname, OUT = path.join(DIR, 'out');
const args = process.argv.slice(2), preview = args.includes('--preview');
let ids = args.filter(a => !a.startsWith('--'));
if (!ids.length) ids = ['01','02','03','04','05','06','07','08','09','10'];
const PAR = +(process.env.PAR || 2);
fs.mkdirSync(OUT, { recursive: true });

async function one(browser, id) {
  const page = await browser.newPage({ viewport: { width: 540, height: 960 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(DIR, 'index.html') + '?v=' + id);
  await page.evaluate(() => document.fonts.ready);
  const meta = await page.evaluate(() => window.META);
  if (preview) {
    const ts = []; for (let t = 0.5; t < meta.dur; t += 1.0) ts.push(t);
    const dir = path.join(OUT, 'preview', id); fs.mkdirSync(dir, { recursive: true });
    for (const t of ts) { await page.evaluate(t => seek(t), t); await page.screenshot({ path: path.join(dir, t.toFixed(1).padStart(5, '0') + '.jpg'), type: 'jpeg', quality: 70 }); }
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-pattern_type', 'glob', '-i', dir + '/*.jpg', '-vf', 'scale=270:480,tile=8x3', '-frames:v', '1', path.join(OUT, 'preview', id + '.jpg')]);
    await page.close(); return;
  }
  fs.writeFileSync(path.join(OUT, id + '.json'), JSON.stringify(meta));
  execFileSync('python3', [path.join(DIR, 'music.py'), path.join(OUT, id + '.json'), path.join(OUT, id + '.wav')]);
  const n = Math.ceil(meta.dur * meta.fps);
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(meta.fps), '-c:v', 'mjpeg', '-i', '-',
    '-i', path.join(OUT, id + '.wav'), '-c:v', 'libx264', '-preset', 'medium', '-crf', '19', '-pix_fmt', 'yuv420p', '-profile:v', 'high',
    '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', path.join(OUT, `startegs_tiktok_${id}.mp4`)], { stdio: ['pipe', 'inherit', 'inherit'] });
  const done = new Promise(r => ff.on('close', r));
  for (let i = 0; i < n; i++) {
    await page.evaluate(t => seek(t), i / meta.fps);
    const buf = await page.screenshot({ type: 'jpeg', quality: 93 });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
  }
  ff.stdin.end(); await done; await page.close();
  fs.unlinkSync(path.join(OUT, id + '.wav')); fs.unlinkSync(path.join(OUT, id + '.json'));
  console.log('done', id, meta.dur.toFixed(1) + 's');
}
(async () => {
  const browser = await chromium.launch();
  const q = [...ids];
  await Promise.all(Array.from({ length: PAR }, async () => { while (q.length) await one(browser, q.shift()); }));
  await browser.close();
})();
