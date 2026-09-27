const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const {MysqlStore} = require('./db/mysql-store');
const {ensureStorage, saveDataUrl, readStored, ossEnabled} = require('./storage');

const PORT = Number(process.env.PORT || 8787);
const ROOT = path.join(__dirname, '..');
const PUBLIC = path.join(ROOT, 'web');
const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = process.env.FIELDLEDGER_DB || path.join(DATA_DIR, 'database.json');
const UPLOAD_DIR = process.env.FIELDLEDGER_UPLOADS || path.join(PUBLIC, 'uploads');
const mysqlStore = process.env.DATABASE_URL ? new MysqlStore(process.env.DATABASE_URL) : null;
const sessions = new Map();
const now = () => new Date().toISOString();
const defaults = () => ({
  organization: { id: 1, code: 'ORG001', name: 'Green Origin Trading', defaultLanguage: 'en' },
  users: [{ id: 1, organizationId: 1, username: 'owner', passwordHash: hash('demo123'), displayName: 'Amina Diallo', language: 'en', status: 'ACTIVE', role: 'OWNER' }],
  farmers: [
    { id: 1, organizationId: 1, farmerNo: 'F000001', name: 'John Koffi', phone: '+225 07 10 20 30', village: 'Abengourou', idNumber: '', notes: 'Long-term cocoa supplier', createdAt: '2026-08-12T08:00:00.000Z', updatedAt: '2026-08-12T08:00:00.000Z' },
    { id: 2, organizationId: 1, farmerNo: 'F000002', name: 'Marie Kouassi', phone: '+225 05 22 48 10', village: 'Daloa', idNumber: '', notes: '', createdAt: '2026-08-21T08:00:00.000Z', updatedAt: '2026-08-21T08:00:00.000Z' }
  ],
  products: [
    { id: 1, organizationId: 1, code: 'COCOA', nameEn: 'Cocoa', nameFr: 'Cacao', defaultUnit: 'kg', active: true, sortOrder: 1 },
    { id: 2, organizationId: 1, code: 'COFFEE', nameEn: 'Coffee', nameFr: 'Café', defaultUnit: 'kg', active: true, sortOrder: 2 }
  ],
  indicators: [
    { id: 1, productId: 1, code: 'MOISTURE', nameEn: 'Moisture', nameFr: 'Humidité', dataType: 'NUMBER', unit: '%', required: true, options: [], active: true },
    { id: 2, productId: 1, code: 'FERMENTATION', nameEn: 'Fermentation', nameFr: 'Fermentation', dataType: 'SELECT', unit: '', required: true, options: ['Good','Average','Poor'], active: true },
    { id: 3, productId: 1, code: 'GRADE', nameEn: 'Quality grade', nameFr: 'Qualité', dataType: 'SELECT', unit: '', required: true, options: ['A','B','C'], active: true },
    { id: 4, productId: 2, code: 'MOISTURE', nameEn: 'Moisture', nameFr: 'Humidité', dataType: 'NUMBER', unit: '%', required: true, options: [], active: true },
    { id: 5, productId: 2, code: 'DEFECT_RATE', nameEn: 'Defect rate', nameFr: 'Taux de défaut', dataType: 'NUMBER', unit: '%', required: true, options: [], active: true },
    { id: 6, productId: 2, code: 'GRADE', nameEn: 'Quality grade', nameFr: 'Qualité', dataType: 'SELECT', unit: '', required: true, options: ['A','B','C'], active: true }
  ],
  purchases: [{ id: 1, organizationId: 1, purchaseNo: 'PUR-20260901-0001', farmerId: 1, productId: 1, purchaseDate: '2026-09-01', weight: 120.5, unit: 'kg', bagCount: 4, notes: 'Clean and well dried.', status: 'CONFIRMED', qualityValues: [{indicatorId:1,name:'Moisture',unit:'%',value:'7.5'},{indicatorId:2,name:'Fermentation',unit:'',value:'Good'},{indicatorId:3,name:'Quality grade',unit:'',value:'A'}], images: [], signature: { dataUrl: '', signedAt: '2026-09-01T10:30:00.000Z' }, createdBy: 1, createdAt: '2026-09-01T10:20:00.000Z', updatedAt: '2026-09-01T10:30:00.000Z', confirmedAt: '2026-09-01T10:30:00.000Z' }]
});

function hash(value) { return crypto.createHash('sha256').update(value).digest('hex'); }
function ensureData() { fs.mkdirSync(path.dirname(DB_FILE), {recursive:true}); ensureStorage(); if (!fs.existsSync(DB_FILE)) fs.writeFileSync(DB_FILE, JSON.stringify(defaults(), null, 2)); }
async function readDb() { return mysqlStore ? mysqlStore.read() : JSON.parse(fs.readFileSync(DB_FILE, 'utf8')); }
async function writeDb(db) { if (mysqlStore) await mysqlStore.write(db); else fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2)); }
function json(res, status, body) { res.writeHead(status, {'Content-Type':'application/json; charset=utf-8'}); res.end(JSON.stringify(body)); }
function body(req) { return new Promise((resolve,reject) => { let raw=''; req.on('data', c => { raw += c; if(raw.length > 12_000_000) req.destroy(); }); req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch(e) { reject(e); } }); req.on('error', reject); }); }
function auth(req) { const token=(req.headers.authorization||'').replace(/^Bearer /,''); return sessions.get(token); }
function nextId(rows) { return rows.reduce((m,x)=>Math.max(m,x.id),0)+1; }
function enrich(db, p) { return {...p, farmer: db.farmers.find(x=>x.id===p.farmerId), product: db.products.find(x=>x.id===p.productId)}; }
async function api(req,res,url) {
  const db=await readDb(); const method=req.method;
  if(method==='POST' && url.pathname==='/api/v1/auth/login') { const b=await body(req); const u=db.users.find(x=>x.username===b.username && x.passwordHash===hash(b.password||'')); if(!u) return json(res,401,{message:'Invalid username or password'}); if(u.status!=='ACTIVE') return json(res,403,{message:'Account is disabled'}); const token=crypto.randomBytes(24).toString('hex'); sessions.set(token,{userId:u.id,organizationId:u.organizationId}); u.lastLoginAt=now(); await writeDb(db); return json(res,200,{token,user:{id:u.id,displayName:u.displayName,language:u.language,role:u.role},organization:db.organization}); }
  const session=auth(req); if(!session) return json(res,401,{message:'Authentication required'});
  if(method==='GET' && url.pathname==='/api/v1/media') {
    const key=url.searchParams.get('key')||'';
    const allowed=db.purchases.some(p=>p.organizationId===session.organizationId && ((p.images||[]).includes(key)||p.signature?.dataUrl===key));
    if(!allowed)return json(res,404,{message:'Media not found'});
    const media=await readStored(key);
    res.writeHead(200,{'Content-Type':media.contentType,'Cache-Control':'private, max-age=3600'});
    return res.end(media.data);
  }
  if(method==='POST' && url.pathname==='/api/v1/auth/logout') { const token=(req.headers.authorization||'').replace(/^Bearer /,''); sessions.delete(token); return json(res,200,{ok:true}); }
  if(method==='GET' && url.pathname==='/api/v1/me') return json(res,200,{user:db.users.find(x=>x.id===session.userId),organization:db.organization});
  if(url.pathname==='/api/v1/farmers' && method==='GET') { const q=(url.searchParams.get('q')||'').toLowerCase(); const rows=db.farmers.filter(x=>x.organizationId===session.organizationId && (!q || [x.name,x.phone,x.farmerNo,x.village].some(v=>(v||'').toLowerCase().includes(q)))).map(f=>({...f,purchaseCount:db.purchases.filter(p=>p.farmerId===f.id).length,lastPurchaseDate:db.purchases.filter(p=>p.farmerId===f.id).map(p=>p.purchaseDate).sort().pop()||null})); return json(res,200,rows); }
  if(url.pathname==='/api/v1/farmers' && method==='POST') { const b=await body(req); if(!b.name?.trim()||!b.village?.trim()) return json(res,422,{message:'Name and village are required'}); const id=nextId(db.farmers); const f={id,organizationId:session.organizationId,farmerNo:`F${String(id).padStart(6,'0')}`,name:b.name.trim(),phone:b.phone||'',village:b.village.trim(),idNumber:b.idNumber||'',notes:b.notes||'',createdBy:session.userId,createdAt:now(),updatedAt:now()}; db.farmers.push(f); await writeDb(db); return json(res,201,f); }
  let match=url.pathname.match(/^\/api\/v1\/farmers\/(\d+)$/); if(match) { const f=db.farmers.find(x=>x.id===+match[1]&&x.organizationId===session.organizationId); if(!f)return json(res,404,{message:'Farmer not found'}); if(method==='GET')return json(res,200,{...f,purchases:db.purchases.filter(p=>p.farmerId===f.id).map(p=>enrich(db,p))}); if(method==='PUT'){Object.assign(f,await body(req),{id:f.id,organizationId:f.organizationId,updatedAt:now()});await writeDb(db);return json(res,200,f);} }
  if(url.pathname==='/api/v1/products'&&method==='GET') return json(res,200,db.products.filter(x=>x.organizationId===session.organizationId).map(p=>({...p,indicators:db.indicators.filter(i=>i.productId===p.id)})));
  match=url.pathname.match(/^\/api\/v1\/products\/(\d+)\/quality-indicators$/); if(match&&method==='GET') return json(res,200,db.indicators.filter(x=>x.productId===+match[1]&&x.active));
  if(url.pathname==='/api/v1/purchases'&&method==='GET') { let rows=db.purchases.filter(x=>x.organizationId===session.organizationId); for(const [key,field] of [['farmerId','farmerId'],['productId','productId']]) if(url.searchParams.get(key)) rows=rows.filter(x=>x[field]===+url.searchParams.get(key)); if(url.searchParams.get('status')) rows=rows.filter(x=>x.status===url.searchParams.get('status')); if(url.searchParams.get('from')) rows=rows.filter(x=>x.purchaseDate>=url.searchParams.get('from')); if(url.searchParams.get('to')) rows=rows.filter(x=>x.purchaseDate<=url.searchParams.get('to')); return json(res,200,rows.sort((a,b)=>b.purchaseDate.localeCompare(a.purchaseDate)||b.id-a.id).map(p=>enrich(db,p))); }
  if(url.pathname==='/api/v1/purchases'&&method==='POST') { const b=await body(req); if(!db.farmers.some(x=>x.id===+b.farmerId)||!db.products.some(x=>x.id===+b.productId)||!(+b.weight>0))return json(res,422,{message:'Farmer, product and a positive weight are required'}); const id=nextId(db.purchases); const date=b.purchaseDate||new Date().toISOString().slice(0,10); const seq=db.purchases.filter(x=>x.purchaseDate===date).length+1; const p={id,organizationId:session.organizationId,purchaseNo:`PUR-${date.replaceAll('-','')}-${String(seq).padStart(4,'0')}`,farmerId:+b.farmerId,productId:+b.productId,purchaseDate:date,weight:+b.weight,unit:b.unit||'kg',bagCount:+b.bagCount||null,notes:b.notes||'',status:'DRAFT',qualityValues:b.qualityValues||[],images:await Promise.all((b.images||[]).map((x,i)=>saveDataUrl(x,`purchase-${id}/photo-${i+1}`))),signature:null,createdBy:session.userId,createdAt:now(),updatedAt:now(),confirmedAt:null,voidedAt:null}; db.purchases.push(p);await writeDb(db);return json(res,201,enrich(db,p)); }
  match=url.pathname.match(/^\/api\/v1\/purchases\/(\d+)$/); if(match) { const p=db.purchases.find(x=>x.id===+match[1]&&x.organizationId===session.organizationId); if(!p)return json(res,404,{message:'Purchase not found'}); if(method==='GET')return json(res,200,enrich(db,p)); if(method==='PUT'){if(p.status!=='DRAFT')return json(res,409,{message:'Confirmed purchases are locked'});Object.assign(p,await body(req),{id:p.id,status:'DRAFT',updatedAt:now()});await writeDb(db);return json(res,200,enrich(db,p));} }
  match=url.pathname.match(/^\/api\/v1\/purchases\/(\d+)\/signature$/); if(match&&method==='POST'){const p=db.purchases.find(x=>x.id===+match[1]);if(!p||p.status!=='DRAFT')return json(res,409,{message:'Purchase cannot be signed'});const b=await body(req);p.signature={dataUrl:await saveDataUrl(b.dataUrl,`purchase-${p.id}/signature`),signedAt:now()};await writeDb(db);return json(res,200,p.signature);}
  match=url.pathname.match(/^\/api\/v1\/purchases\/(\d+)\/confirm$/); if(match&&method==='POST'){const p=db.purchases.find(x=>x.id===+match[1]);if(!p||p.status!=='DRAFT')return json(res,409,{message:'Purchase cannot be confirmed'});const required=db.indicators.filter(i=>i.productId===p.productId&&i.required);const set=new Set(p.qualityValues.map(v=>+v.indicatorId));if(!p.images.length||!p.signature||required.some(i=>!set.has(i.id)))return json(res,422,{message:'Photo, signature and all required quality values are needed'});p.status='CONFIRMED';p.confirmedAt=now();p.updatedAt=now();await writeDb(db);return json(res,200,enrich(db,p));}
  match=url.pathname.match(/^\/api\/v1\/purchases\/(\d+)\/void$/); if(match&&method==='POST'){const p=db.purchases.find(x=>x.id===+match[1]);const b=await body(req);if(!p||p.status!=='CONFIRMED'||!b.reason?.trim())return json(res,422,{message:'A confirmed purchase and reason are required'});p.status='VOIDED';p.voidReason=b.reason.trim();p.voidedAt=now();p.voidedBy=session.userId;await writeDb(db);return json(res,200,enrich(db,p));}
  if(url.pathname==='/api/v1/export/purchases.csv'&&method==='GET'){const esc=v=>`"${String(v??'').replaceAll('"','""')}"`;const lines=[['Purchase No.','Date','Farmer ID','Farmer Name','Product','Weight','Unit','Quality Indicators','Notes','Status','Photo URLs','Signature URL']];for(const p of db.purchases){const e=enrich(db,p);lines.push([p.purchaseNo,p.purchaseDate,e.farmer.farmerNo,e.farmer.name,e.product.nameEn,p.weight,p.unit,p.qualityValues.map(v=>`${v.name}: ${v.value}${v.unit}`).join('; '),p.notes,p.status,p.images.join('; '),p.signature?.dataUrl||'']);}res.writeHead(200,{'Content-Type':'text/csv; charset=utf-8','Content-Disposition':'attachment; filename="purchases.csv"'});return res.end('\ufeff'+lines.map(r=>r.map(esc).join(',')).join('\n'));}
  if(url.pathname==='/api/v1/stats'&&method==='GET'){const active=db.purchases.filter(p=>p.status==='CONFIRMED');return json(res,200,{farmers:db.farmers.length,purchases:active.length,totalWeight:active.reduce((s,p)=>s+p.weight,0),monthWeight:active.filter(p=>p.purchaseDate.slice(0,7)===new Date().toISOString().slice(0,7)).reduce((s,p)=>s+p.weight,0)});}
  if(url.pathname==='/api/v1/users'&&method==='GET')return json(res,200,db.users.map(({passwordHash,...u})=>u));
  return json(res,404,{message:'Not found'});
}

function staticFile(req,res,url){let rel=url.pathname==='/'?'index.html':decodeURIComponent(url.pathname.slice(1));let file=path.normalize(path.join(PUBLIC,rel));if(!file.startsWith(PUBLIC))return json(res,403,{message:'Forbidden'});if(!fs.existsSync(file)||fs.statSync(file).isDirectory())file=path.join(PUBLIC,'index.html');const ext=path.extname(file);const types={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.png':'image/png','.jpg':'image/jpeg','.svg':'image/svg+xml'};res.writeHead(200,{'Content-Type':types[ext]||'application/octet-stream'});fs.createReadStream(file).pipe(res);}
function createServer(){
  let ready;
  if(mysqlStore) ready=mysqlStore.initialize().then(async()=>{if(await mysqlStore.isEmpty())await mysqlStore.write(defaults());});
  else {ensureData();ready=Promise.resolve();}
  let queue=Promise.resolve();
  return http.createServer(async(req,res)=>{const url=new URL(req.url,`http://${req.headers.host}`);try{await ready;if(url.pathname.startsWith('/api/')){const run=queue.then(()=>api(req,res,url));queue=run.catch(()=>{});await run;}else staticFile(req,res,url);}catch(e){console.error(e);json(res,500,{message:'Server error'});}});
}
if(require.main===module)createServer().listen(PORT,'0.0.0.0',()=>console.log(`Harvest Purchase Manager: http://localhost:${PORT}\nStorage: ${ossEnabled?'OSS':'local'}`));
module.exports={createServer,defaults};
