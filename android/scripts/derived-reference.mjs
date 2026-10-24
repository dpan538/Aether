// Additional state only: read the immutable source, never write its archive.
import fs from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const out='verification/local/derived-reference';
await fs.mkdir(out,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
try {
  const page=await browser.newPage({viewport:{width:1704,height:1136},deviceScaleFactor:2,reducedMotion:'reduce'});
  await page.goto('http://127.0.0.1:4177/modern/index.html');
  await page.waitForFunction(()=>document.body.dataset.ready==='true');
  await page.click('#device-light');
  await page.click('.favorite-station[data-station="3"]');
  await page.waitForTimeout(1200);
  await page.screenshot({path:out+'/preset004.png',animations:'disabled'});
  await fs.writeFile(out+'/recipe.json',JSON.stringify({source:'design/snapshots/2026-10-24-two-page-baseline/source',viewport:[1704,1136],deviceScaleFactor:2,reducedMotion:'reduce',actions:['wait body.dataset.ready','click #device-light','click .favorite-station[data-station="3"]','wait 1200ms'],note:'Derived state, not an original frozen render; no source changes.'},null,2));
} finally {await browser.close();}
