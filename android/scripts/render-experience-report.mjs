import path from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'playwright');
const folder=path.resolve(process.argv[2]||'verification/local/experience-300');
const expected=Number(process.argv[3]||300);
const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
try{
 const page=await browser.newPage({viewport:{width:1400,height:1000},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(pathToFileURL(path.join(folder,'report.html')).href);
 const records=await page.locator('#records details').count();
 if(records!==expected)throw Error(`Expected ${expected} actual round records, found ${records}`);
 await page.locator('#status').selectOption('fail');
 const failures=await page.locator('#records details').count();
 await page.locator('#status').selectOption('all');
 await page.screenshot({path:path.join(folder,'report-preview.png'),fullPage:false});
 const charts=await page.locator('svg').all();
 for(let i=0;i<charts.length;i++)await charts[i].screenshot({path:path.join(folder,'charts',`chart-${i+1}.png`)});
 if(errors.length)throw Error(errors.join('\n'));
 console.log(JSON.stringify({records,failures,charts:charts.length,pageErrors:errors}));
}finally{await browser.close()}
