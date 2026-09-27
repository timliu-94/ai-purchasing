const fs = require('fs');
const path = require('path');
const OSS = require('ali-oss');

const localDir = process.env.FIELDLEDGER_UPLOADS || path.join(__dirname, '..', 'web', 'uploads');
const ossEnabled = Boolean(process.env.OSS_BUCKET && process.env.OSS_ACCESS_KEY_ID && process.env.OSS_ACCESS_KEY_SECRET);
const oss = ossEnabled ? new OSS({
  region: process.env.OSS_REGION || 'oss-ap-southeast-1',
  endpoint: process.env.OSS_ENDPOINT || undefined,
  internal: process.env.OSS_INTERNAL === 'true',
  bucket: process.env.OSS_BUCKET,
  accessKeyId: process.env.OSS_ACCESS_KEY_ID,
  accessKeySecret: process.env.OSS_ACCESS_KEY_SECRET,
  secure: true
}) : null;

function ensureStorage() {
  if (!ossEnabled) fs.mkdirSync(localDir, {recursive:true});
}

async function saveDataUrl(dataUrl, stem) {
  if (!dataUrl) return '';
  const match = dataUrl.match(/^data:([^;]+);base64,(.+)$/);
  if (!match) return dataUrl;
  const ext = match[1].includes('png') ? 'png' : 'jpg';
  const name = `${stem}-${Date.now()}.${ext}`;
  const contents = Buffer.from(match[2], 'base64');
  if (oss) {
    const key = `fieldledger/${name}`;
    await oss.put(key, contents, {headers:{'Content-Type':match[1]}});
    return key;
  }
  const target = path.join(localDir, name);
  fs.mkdirSync(path.dirname(target), {recursive:true});
  fs.writeFileSync(target, contents);
  return `/uploads/${name}`;
}

async function readStored(key) {
  if (!key) throw new Error('Missing media key');
  if (oss) {
    const result = await oss.get(key);
    return {
      data: result.content,
      contentType: result.res?.headers?.['content-type'] || (key.endsWith('.png') ? 'image/png' : 'image/jpeg')
    };
  }
  const relative = key.replace(/^\/uploads\//, '');
  const target = path.resolve(localDir, relative);
  const root = path.resolve(localDir) + path.sep;
  if (!target.startsWith(root)) throw new Error('Invalid media key');
  return {data: await fs.promises.readFile(target), contentType: key.endsWith('.png') ? 'image/png' : 'image/jpeg'};
}

module.exports = {ensureStorage, saveDataUrl, readStored, ossEnabled};
