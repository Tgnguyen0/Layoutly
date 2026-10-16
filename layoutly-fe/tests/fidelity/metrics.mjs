import { PNG } from 'pngjs'
import pixelmatch from 'pixelmatch'
import ssimPackage from 'ssim.js'
const ssim = ssimPackage.ssim || ssimPackage.default || ssimPackage

export const gates = Object.freeze({schemaVersion:1,pixelThreshold:0.1,maxPixelDiffRatio:0.01,minSsim:0.99,maxGeometryErrorPx:1})
export function comparePng(expectedBytes,actualBytes) {
  const expected=PNG.sync.read(expectedBytes), actual=PNG.sync.read(actualBytes)
  if(expected.width!==actual.width||expected.height!==actual.height) return {metrics:{dimensionsMatch:false,pixelDiffRatio:null,ssim:null,pass:false},diff:null,overlay:null}
  const {width,height}=actual
  const diff=new PNG({width,height}), overlay=new PNG({width,height})
  const different=pixelmatch(expected.data,actual.data,diff.data,width,height,{threshold:gates.pixelThreshold,includeAA:true})
  // Compare on a white matte; raw transparent RGB values must not affect SSIM.
  const matte = png => {
    const data=new Uint8ClampedArray(png.data.length)
    for(let i=0;i<data.length;i+=4){const alpha=png.data[i+3]/255; for(let c=0;c<3;c++)data[i+c]=Math.round(png.data[i+c]*alpha+255*(1-alpha)); data[i+3]=255}
    return {width,height,data}
  }
  const left=matte(expected),right=matte(actual)
  for(let i=0;i<overlay.data.length;i+=4){for(let c=0;c<3;c++)overlay.data[i+c]=Math.round((left.data[i+c]+right.data[i+c])/2);overlay.data[i+3]=255}
  const similarity=ssim(left,right,{ssim:'bezkrovny'}).mssim
  const ratio=different/(width*height)
  return {metrics:{dimensionsMatch:true,pixelDiffRatio:ratio,differentPixels:different,ssim:similarity,pass:ratio<=gates.maxPixelDiffRatio&&similarity>=gates.minSsim},diff:PNG.sync.write(diff),overlay:PNG.sync.write(overlay)}
}
export function summarize(samples) {
  if(!samples.length)return {samples:0,p50:null,p95:null}
  const sorted=[...samples].sort((a,b)=>a-b)
  const at=p=>sorted[Math.max(0,Math.ceil(p*sorted.length)-1)]
  return {samples:sorted.length,p50:at(.5),p95:sorted.length>=20?at(.95):null,min:sorted[0],max:sorted.at(-1)}
}
export function fidelityEligibility({reference,missingFonts=[],missingAssets=[],unsupported=[],runtimeErrors=[],geometryPass=true,rootDimensionsMatch=true,parityPass=true}) {
  const reasons=[]
  if(!reference) reasons.push('MISSING_FIGMA_REFERENCE')
  if(missingFonts.length)reasons.push('MISSING_OR_UNVERIFIED_FONTS')
  if(missingAssets.length)reasons.push('MISSING_ASSETS')
  if(unsupported.length)reasons.push('UNSUPPORTED_FEATURES')
  if(runtimeErrors.length)reasons.push('RUNTIME_ERRORS')
  if(!geometryPass)reasons.push('GEOMETRY_REGRESSION')
  if(!rootDimensionsMatch)reasons.push('ROOT_DIMENSIONS_MISMATCH')
  if(!parityPass)reasons.push('HTML_REACT_PARITY_REGRESSION')
  return {eligible:reasons.length===0,reasons}
}
export function referenceEvidence(spec) {
  if(!spec.reference)return {valid:false,reason:'MISSING_FIGMA_REFERENCE'}
  const ref=spec.reference
  if(!ref.path||!/^[a-f0-9]{64}$/.test(ref.sha256||''))return {valid:false,reason:'REFERENCE_PATH_OR_HASH_MISSING'}
  if(ref.sourceRevision!==spec.sourceRevision||ref.rootId!==(spec.displayRootId||spec.rootId)||ref.scale!==1)return {valid:false,reason:'REFERENCE_ROOT_REVISION_OR_SCALE_MISMATCH'}
  return {valid:true}
}
