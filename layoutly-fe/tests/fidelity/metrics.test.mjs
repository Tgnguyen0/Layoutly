import {test} from 'node:test'
import assert from 'node:assert/strict'
import {PNG} from 'pngjs'
import {comparePng,summarize,fidelityEligibility,referenceEvidence} from './metrics.mjs'
const image=(value,w=32,h=32)=>{const p=new PNG({width:w,height:h});for(let i=0;i<p.data.length;i+=4){p.data[i]=p.data[i+1]=p.data[i+2]=value;p.data[i+3]=255}return PNG.sync.write(p)}
test('identity passes both metrics; complete mismatch fails',()=>{assert.equal(comparePng(image(0),image(0)).metrics.ssim,1);assert.equal(comparePng(image(0),image(0)).metrics.pixelDiffRatio,0);const d=comparePng(image(0),image(255));assert.equal(d.metrics.pixelDiffRatio,1);assert.equal(d.metrics.pass,false);assert(d.metrics.ssim<.01)})
test('size mismatch is never rescaled into a pass',()=>{assert.equal(comparePng(image(0),image(0,64)).metrics.pass,false);assert.equal(comparePng(image(0),image(0,64)).metrics.ssim,null)})
test('a localized regression is detected; comparison does not mutate either input',()=>{const a=image(255);const p=PNG.sync.read(a);for(let y=0;y<8;y++)for(let x=0;x<8;x++){let i=(y*32+x)*4;p.data[i]=p.data[i+1]=p.data[i+2]=0}const b=PNG.sync.write(p);const copy=Buffer.from(a);assert.equal(comparePng(a,b).metrics.pass,false);assert.deepEqual(a,copy)})
test('small samples cannot claim p95',()=>{assert.equal(summarize([1,2]).p95,null);assert.equal(summarize(Array.from({length:20},(_,i)=>i+1)).p95,19)})
test('missing evidence cannot receive a fidelity score even when outputs match',()=>{assert.equal(fidelityEligibility({reference:null}).eligible,false);assert.equal(fidelityEligibility({reference:'ref.png',missingFonts:['Inter']}).eligible,false);assert.equal(fidelityEligibility({reference:'ref.png'}).eligible,true)})
test('a reference must identify the exact root, source revision, scale and reviewed hash',()=>{
  const spec={sourceRevision:'v1',rootId:'root',reference:{path:'reference.png',sha256:'a'.repeat(64),rootId:'root',sourceRevision:'v1',scale:1}}
  assert.equal(referenceEvidence(spec).valid,true)
  assert.equal(referenceEvidence({...spec,sourceRevision:'v2'}).valid,false)
  assert.equal(referenceEvidence({...spec,rootId:'other'}).valid,false)
  assert.equal(referenceEvidence({...spec,reference:{...spec.reference,scale:2}}).valid,false)
})
test('reference image similarity cannot override failed geometry/root/parity gates',()=>{
  for(const gate of ['geometryPass','rootDimensionsMatch','parityPass'])assert.equal(fidelityEligibility({reference:'ref.png',[gate]:false}).eligible,false)
})
