import {readFile,writeFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {radius,pole,axisU,axisV,definitions,curves,paths,renderStructure,renderArtwork} from './grid.mjs';
const sharp=createRequire(import.meta.url)('sharp'),root=new URL('./',import.meta.url);
const gridSVG=renderStructure();
await writeFile(new URL('shared-structure.svg',root),gridSVG);
await sharp(Buffer.from(gridSVG)).png().toFile(new URL('exports/shared-structure.png',root).pathname);
const texture=await readFile(new URL('auxiliary/auxiliary-spectrum-field.png',root));
const svg=renderArtwork('data:image/png;base64,'+texture.toString('base64'));
await writeFile(new URL('E2-v2-shared-globe.svg',root),svg);
await sharp(Buffer.from(svg)).png().toFile(new URL('exports/E2-v2-shared-globe.png',root).pathname);
let maximumSphereRadiusError=0;
for(const curve of definitions)for(let i=0;i<=200;i++){
 const p=curve.fn(curve.a+(curve.b-curve.a)*i/200);
 maximumSphereRadiusError=Math.max(maximumSphereRadiusError,Math.abs(Math.hypot(...p)-1));
}
const verification={date:'2026-10-25',projection:'Orthographic projection of one unit sphere',center:[512,512],radius,pole,axisU,axisV,curveCount:curves.length,gridSHA256:createHash('sha256').update(paths).digest('hex'),sharedDefinition:'shared-spherical-grid',upperReference:'shared-spherical-grid',lowerReference:'shared-spherical-grid',independentHalfTransforms:false,geometryDifferenceByConstruction:0,maximumSphereRadiusError,frontCenterlineCrossings:curves.flatMap(c=>c.crossings),note:'Both halves use the same complete projected paths; color masks only. No raster pixel-accuracy claim. Texture is imagegen output; contour geometry is authored.'};
await writeFile(new URL('structure-verification.json',root),JSON.stringify(verification,null,2)+'\n');
console.log(`Exported one shared sphere (${curves.length} curves); both color regions reference it.`);
