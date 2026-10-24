// Icon-study fork of disc-original.js; the approved page and snapshot are unchanged.
const vertexSource = `#version 300 es
in vec2 position;
void main(){gl_Position=vec4(position,0.,1.);}`;

const fragmentSource = `#version 300 es
precision highp float;
uniform vec2 resolution;
uniform float time;
uniform float material;
uniform float distortion;
out vec4 outputColor;
const float PI=3.14159265359;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}
vec3 foilColor(float phase){
 const vec3 colors[13]=vec3[13](
  vec3(.13,.36,.89),vec3(.07,.32,.61),vec3(.035,.17,.15),
  vec3(.035,.19,.15),vec3(.13,.48,.32),vec3(.65,.72,.29),
  vec3(.94,.78,.24),vec3(.96,.48,.25),vec3(.92,.29,.35),
  vec3(.91,.47,.68),vec3(.55,.49,.90),vec3(.20,.36,.95),vec3(.13,.36,.89)
 );
 float p=fract(phase)*12.;int segment=int(floor(p));
 return mix(colors[segment],colors[segment+1],smoothstep(0.,1.,fract(p)));
}
float band(float r,float a,float b){return smoothstep(a-.001,a+.001,r)*(1.-smoothstep(b-.001,b+.001,r));}
float line(float r,float at,float width){return 1.-smoothstep(width,width+max(fwidth(r),.00015),abs(r-at));}
// Printed label face, with the 15:120 spindle-hole ratio retained.
const float HOLE=15./120.;
const float RIM_IN=118./120.;
float valueNoise(vec2 p){
 vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
 return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);
}
float stipple(vec2 p,float scale,float density,float seed){
 vec2 cell=floor(p*scale),f=fract(p*scale);
 float dots=0.;
 for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
  vec2 offset=vec2(float(x),float(y));
  vec2 id=cell+offset;
  float dotEnabled=step(.00001,density)*step(hash(id+seed),density);
  vec2 center=offset+.18+.64*vec2(hash(id+seed+31.2),hash(id+seed+73.8));
  float radius=.10+.30*pow(hash(id+seed+18.7),.7);
  float dist=length(f-center);
  float aa=max(fwidth(dist),.008);
  dots=max(dots,dotEnabled*(1.-smoothstep(radius-aa,radius+aa,dist)));
 }
 return dots;
}
vec3 printedDisc(vec2 p,float r,float theta){
 // Aether's lunar composition: two offset circles define the crescent.
 // Echo contours and pigment drift relate it to frequencies, not a copied blob.
 const float tilt=.23;
 vec2 q=vec2(cos(tilt)*p.x-sin(tilt)*p.y,sin(tilt)*p.x+cos(tilt)*p.y);
 float cutRadius=length(q+vec2(.31,0.));
 float distance=cutRadius-1.04;
 distance+=(valueNoise(q*62.)-.5)*.004;
 float core=1.-smoothstep(-.004,.004,distance);
 float density=exp(-max(distance,0.)*26.)*.84;
 vec2 drift=q+vec2(.014*sin(q.y*7.),.008*sin(q.x*5.));
 float cloud=stipple(drift,108.,density,27.3);
 cloud=max(cloud,stipple(drift,210.,density*.66,71.9));
 cloud=max(cloud,stipple(drift,54.,density*.07,123.5));
 cloud*=smoothstep(-.014,.009,distance);
 float ink=max(core,cloud);
 float erosion=stipple(q+vec2(.02,.04),164.,.29,151.7);
 erosion*=exp(-pow((distance+.006)/.015,2.));
 ink*=1.-erosion;
 float printArea=1.-smoothstep(.947,.951,r);
 float grain=hash(gl_FragCoord.xy)-.5;
 float broad=valueNoise(p*4.7);
 float lunarTone=clamp((q.y+.9)/1.8,0.,1.);
 vec3 lunar=mix(vec3(.92,.87,.64),vec3(.64,.76,.72),smoothstep(.05,.95,lunarTone));
 lunar=mix(lunar,vec3(.32,.68,.91),smoothstep(.64,.98,lunarTone)*.42);
 lunar+=grain*.005;
 vec3 night=mix(vec3(.012,.019,.031),vec3(.033,.050,.071),.25+broad*.55);
 night+=grain*.0022;
 vec3 color=mix(night,lunar,(1.-clamp(ink,0.,1.))*printArea);
 // Two fading echo arcs live on the dark side of the phase boundary.
 float echoMask=smoothstep(.03,.39,q.x)*(1.-smoothstep(.72,.94,abs(q.y)))*printArea;
 float echo=(line(cutRadius,.987,.00065)*.38+line(cutRadius,.929,.00055)*.21)*echoMask;
 vec3 electric=vec3(.055,.64,1.);
 color+=electric*echo*.90;
 float blueEdge=line(cutRadius,1.04,.0012)*echoMask;
 float blueHalo=exp(-pow(distance/.014,2.))*echoMask;
 color=mix(color,electric,blueEdge*.55);
 color+=electric*blueHalo*.055;
 float dustDensity=exp(-pow((distance+.045)/.045,2.))*.12;
 float dust=stipple(q,146.,dustDensity,207.1)*step(distance,0.)*printArea;
 color=mix(color,mix(lunar,electric,.60),dust*.68);
 float fieldDust=stipple(q,92.,.0045,316.2)*core*printArea;
 color=mix(color,mix(lunar,electric,.45),fieldDust*.30);
 // A printed, matte face with a restrained cool metal lip and clear hole.
 float rim=smoothstep(.978,.983,r);
 float glint=.5+.5*cos(theta+2.1);
 vec3 metal=mix(vec3(.13,.18,.23),vec3(.69,.77,.80),glint);
 color=mix(color,metal,rim*.94);
 color=mix(color,vec3(.80,.88,.92),line(r,.987,.0018)*.82);
 color=mix(color,vec3(.035,.065,.09),line(r,.995,.0013)*.88);
 color=mix(color,vec3(.49,.69,.82),line(r,.998,.0008)*.68);
 float collar=band(r,HOLE+.003,.151);
 vec3 plastic=mix(vec3(.20,.28,.34),vec3(.69,.78,.81),.5+.5*cos(theta+2.3));
 color=mix(color,plastic,collar*.88);
 color=mix(color,vec3(.04,.07,.10),line(r,.153,.0017)*.72);
 color=mix(color,vec3(.76,.86,.90),line(r,HOLE+.003,.0016)*.82);
 color*=1.-line(r,HOLE+.007,.0008)*.24;
 return clamp(color,0.,1.);
}
void main(){
 vec2 p=(gl_FragCoord.xy/resolution-.5)*2.;
 p.y=-p.y;
 float r=length(p);
 if(r>1.){outputColor=vec4(0.);return;}
 float theta=atan(p.y,p.x);
 if(material>.5){
  float alpha=(1.-smoothstep(.9975,1.,r))*smoothstep(HOLE-.001,HOLE+.001,r);
  outputColor=vec4(printedDisc(p,r,theta),alpha);
  return;
 }
 float t=time*.006;
 vec3 forest=mix(vec3(.018,.039,.049),vec3(.062,.112,.121),clamp(.46+p.y*.42,0.,1.));
 vec3 color=forest;
 // A shared three-beat wave bends color phase, keeping the round silhouette.
 float rhythm=.065*sin(theta*3.+(r-.35)*8.)+.025*sin(theta*6.-r*4.);
 float hue=theta/(2.*PI)*3.-t+distortion*rhythm;
 vec3 foil=foilColor(hue);
 float luminosity=.93+.07*cos(theta-.6)+.015*sin(r*17.);
 vec3 metal=foil*luminosity;
 float rainbow=band(r,.365,.745);
 color=mix(color,metal,rainbow);
 float outerBand=band(r,.887,.933);
 vec3 outer=foilColor(hue+.08)*(.82+.10*cos(theta*2.));
 color=mix(color,outer,outerBand);
 color=mix(color,metal*.35,band(r,.971,.985));
 float echoRadius=r+distortion*.012*sin(theta*3.+r*6.)*smoothstep(.31,.45,r)*(1.-smoothstep(.83,.96,r));
 float etched=line(echoRadius,.305,.00065)+line(echoRadius,.562,.00065)+line(echoRadius,.831,.00065);
 vec3 engraving=vec3(.78,.83,.85);
 color=mix(color,engraving,clamp(etched*.68,0.,.74));
 color=mix(color,forest*.97,band(r,0.,.301));
 color=mix(color,vec3(.008,.035,.049),line(r,.994,.002));
 float weave=sin((gl_FragCoord.x+gl_FragCoord.y)*1.25)*.0015;
 float grain=(hash(gl_FragCoord.xy)-.5)*.017;
 color+=weave+grain;
 color=pow(max(color,vec3(0.)),vec3(.90));
 float edge=1.-smoothstep(.9975,1.,r);
 outputColor=vec4(color,edge);
}`;

export function createDisc(canvas, { animate = false, material = 'spectral', pixelRatio, distortion = 0 } = {}) {
  const gl = canvas.getContext('webgl2', { alpha: true, antialias: true, premultipliedAlpha: false, preserveDrawingBuffer: true });
  if (!gl) { canvas.dataset.renderer = 'css-fallback'; return { resize() {}, destroy() {} }; }
  function shader(type, source) {
    const value = gl.createShader(type);
    gl.shaderSource(value, source);
    gl.compileShader(value);
    if (!gl.getShaderParameter(value, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(value));
    return value;
  }
  const vertex = shader(gl.VERTEX_SHADER, vertexSource);
  const fragment = shader(gl.FRAGMENT_SHADER, fragmentSource);
  const program = gl.createProgram();
  gl.attachShader(program, vertex);
  gl.attachShader(program, fragment);
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);
  const buffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]), gl.STATIC_DRAW);
  const attribute = gl.getAttribLocation(program, 'position');
  gl.enableVertexAttribArray(attribute);
  gl.vertexAttribPointer(attribute, 2, gl.FLOAT, false, 0, 0);
  const resolution = gl.getUniformLocation(program, 'resolution');
  const time = gl.getUniformLocation(program, 'time');
  const materialLocation = gl.getUniformLocation(program, 'material');
  gl.uniform1f(gl.getUniformLocation(program, 'distortion'), distortion);
  gl.uniform1f(materialLocation, material === 'print' ? 1 : 0);
  let frame = 0;
  let last = -Infinity;
  let destroyed = false;
  function draw(milliseconds = 0) {
    if (destroyed) return;
    gl.uniform2f(resolution, canvas.width, canvas.height);
    gl.uniform1f(time, animate ? milliseconds / 1000 : 0);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
  }
  function loop(now) {
    if (destroyed) return;
    if (!document.hidden && now - last >= 1000 / 24) { draw(now); last = now; }
    frame = requestAnimationFrame(loop);
  }
  function resize() {
    const size = canvas.getBoundingClientRect();
    const ratio = pixelRatio ?? Math.min(devicePixelRatio || 1, 2);
    canvas.width = Math.round(size.width * ratio);
    canvas.height = Math.round(size.height * ratio);
    gl.viewport(0, 0, canvas.width, canvas.height);
    draw(performance.now());
  }
  canvas.dataset.renderer = 'webgl2';
  canvas.dataset.material = material;
  if (material === 'print') {
    canvas.dataset.diameterMm = '120';
    canvas.dataset.holeMm = '15';
  }
  resize();
  if (animate) frame = requestAnimationFrame(loop);
  return { resize, destroy() { destroyed = true; cancelAnimationFrame(frame); gl.deleteProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment); gl.deleteBuffer(buffer); } };
}
