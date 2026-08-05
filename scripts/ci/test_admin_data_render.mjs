#!/usr/bin/env node
// Executes the compiled Vue template so subtitle binding regressions fail at render time.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(scriptDir, '../..')
const frontendRoot = path.join(root, 'frontend')
const frontendRequire = createRequire(path.join(frontendRoot, 'package.json'))
const importFrontendPackage = async (name) => import(pathToFileURL(frontendRequire.resolve(name)).href)
const { createServer } = await importFrontendPackage('vite')
const { createSSRApp, h } = await importFrontendPackage('vue')
const { renderToString } = await importFrontendPackage('@vue/server-renderer')
const storage = new Map()
Object.defineProperty(globalThis, 'navigator', { value: { language: 'zh-CN' }, configurable: true })
Object.defineProperty(globalThis, 'localStorage', { value: { getItem:(k)=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,String(v)),removeItem:(k)=>storage.delete(k),clear:()=>storage.clear() }, configurable:true })
Object.defineProperty(globalThis, 'document', { value:{ documentElement:{lang:'zh-CN'}, body:null }, configurable:true })
globalThis.Node=class Node{}; globalThis.Element=class Element{}; globalThis.MutationObserver=class MutationObserver{observe(){} disconnect(){}}
const server=await createServer({root:frontendRoot,appType:'custom',logLevel:'error',server:{middlewareMode:true}})
try {
 const {default:AdminDataView}=await server.ssrLoadModule('/src/views/admin/AdminDataView.vue')
 const warnings=[]; const app=createSSRApp({render:()=>h(AdminDataView)})
 app.component('RouterLink',{props:['to'],setup(_p,{slots}){return()=>h('a',slots.default?.())}})
 app.config.warnHandler=(message)=>warnings.push(String(message))
 const html=await renderToString(app)
 assert.match(html,/基础数据|MASTER DATA/); assert.match(html,/专业与学生/)
 assert.doesNotMatch(warnings.join('\n'),/subtitle.*not a function/i)
 assert.doesNotMatch(warnings.join('\n'),/Property .*subtitle.*was accessed during render/i)
 console.log('AdminDataView real SSR render passed')
} finally { await server.close() }