import {readFile,writeFile,access} from 'node:fs/promises'
import {spawn} from 'node:child_process'
import path from 'node:path'
const output=path.resolve(process.env.LAYOUTLY_FIDELITY_OUTPUT || '../.local-build/fidelity/before')
const index=JSON.parse(await readFile(path.join(output,'index.json'),'utf8'))
const run=(args,cwd)=>new Promise((resolve,reject)=>{
  // npm's JS entry avoids shell interpolation and Windows .cmd quoting issues.
  const cli=process.env.npm_execpath || 'C:/Program Files/nodejs/node_modules/npm/bin/npm-cli.js'
  const child=spawn(process.execPath,[cli,...args],{cwd,windowsHide:true,env:process.env})
  let log='';child.stdout.on('data',d=>log+=d);child.stderr.on('data',d=>log+=d)
  child.on('error',reject);child.on('exit',code=>code===0?resolve(log):reject(new Error(`${cwd}: ${args.join(' ')} failed (${code})\n${log}`)))
})
let sharedLock=process.env.LAYOUTLY_FIDELITY_LOCK ? await readFile(path.resolve(process.env.LAYOUTLY_FIDELITY_LOCK)) : undefined
for(const spec of index) {
  const dir=path.join(output,spec.id,'react')
  await access(path.join(dir,'package.json'))
  // Install into each actual unzipped project. Only the lockfile/cache is shared.
  if(sharedLock)await writeFile(path.join(dir,'package-lock.json'),sharedLock)
  let log=await run(sharedLock?['ci','--ignore-scripts','--no-audit','--no-fund']:['install','--ignore-scripts','--no-audit','--no-fund'],dir)
  if(!sharedLock)sharedLock=await readFile(path.join(dir,'package-lock.json'))
  log+=await run(['run','build','--','--base','./'],dir)
  await writeFile(path.join(output,spec.id,'react-build.log'),log)
  console.log(`ZIP extracted → isolated npm install/ci → build: ${spec.id}`)
}
await writeFile(path.join(output,'export-package-lock.json'),sharedLock)
