const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const os = require('os');
const testRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'fieldledger-test-'));
process.env.FIELDLEDGER_DB = path.join(testRoot, 'database.json');
process.env.FIELDLEDGER_UPLOADS = path.join(testRoot, 'uploads');
const { createServer } = require('./server');

let server, base, token;
test.before(async () => {
  server = createServer().listen(0, '127.0.0.1');
  await new Promise(r => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
test.after(() => { server.close(); fs.rmSync(testRoot,{recursive:true,force:true}); });
async function api(pathname, options={}) {
  const headers = {'Content-Type':'application/json', ...(token?{Authorization:`Bearer ${token}`}:{})};
  const response = await fetch(base+pathname, {...options, headers});
  const data = await response.json();
  return {response,data};
}
async function raw(pathname) {
  return fetch(base+pathname,{headers:{Authorization:`Bearer ${token}`}});
}
test('login and protected resources', async () => {
  let result = await api('/api/v1/farmers');
  assert.equal(result.response.status, 401);
  result = await api('/api/v1/auth/login',{method:'POST',body:JSON.stringify({username:'owner',password:'demo123'})});
  assert.equal(result.response.status, 200); token=result.data.token;
  result = await api('/api/v1/products');
  assert.equal(result.data.length, 2); assert.equal(result.data[0].indicators.length, 3);
});
test('golden purchase flow and lock', async () => {
  const farmer = await api('/api/v1/farmers',{method:'POST',body:JSON.stringify({name:'Test Farmer',village:'Test Village'})});
  assert.match(farmer.data.farmerNo,/^F\d{6}$/);
  const purchase = await api('/api/v1/purchases',{method:'POST',body:JSON.stringify({farmerId:farmer.data.id,productId:1,purchaseDate:'2026-09-06',weight:120.5,unit:'kg',qualityValues:[{indicatorId:1,name:'Moisture',unit:'%',value:'7.5'},{indicatorId:2,name:'Fermentation',value:'Good'},{indicatorId:3,name:'Quality grade',value:'A'}],images:['data:image/png;base64,iVBORw0KGgo=']})});
  assert.equal(purchase.response.status,201); assert.equal(purchase.data.status,'DRAFT');
  await api(`/api/v1/purchases/${purchase.data.id}/signature`,{method:'POST',body:JSON.stringify({dataUrl:'data:image/png;base64,iVBORw0KGgo='})});
  const confirmed=await api(`/api/v1/purchases/${purchase.data.id}/confirm`,{method:'POST',body:'{}'});
  assert.equal(confirmed.data.status,'CONFIRMED');
  const media=await raw(`/api/v1/media?key=${encodeURIComponent(confirmed.data.images[0])}`);
  assert.equal(media.status,200); assert.equal(media.headers.get('content-type'),'image/png');
  const missing=await raw('/api/v1/media?key=not-owned.jpg');
  assert.equal(missing.status,404);
  const edit=await api(`/api/v1/purchases/${purchase.data.id}`,{method:'PUT',body:JSON.stringify({weight:1})});
  assert.equal(edit.response.status,409);
  const voided=await api(`/api/v1/purchases/${purchase.data.id}/void`,{method:'POST',body:JSON.stringify({reason:'Test correction'})});
  assert.equal(voided.data.status,'VOIDED');
});
