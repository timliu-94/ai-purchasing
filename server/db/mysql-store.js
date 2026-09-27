const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

function camel(row) {
  const result = {};
  for (const [key, value] of Object.entries(row)) {
    result[key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())] = value;
  }
  return result;
}

function nullable(value) { return value === undefined ? null : value; }
function dateValue(value) {
  if (value == null || value === '') return null;
  if (value instanceof Date || /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(value)) return value;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed;
}
function jsonValue(value) {
  if (value == null) return [];
  return typeof value === 'string' ? JSON.parse(value) : value;
}

class MysqlStore {
  constructor(url) {
    this.pool = mysql.createPool({uri: url, connectionLimit: 10, dateStrings: true, decimalNumbers: true});
  }

  async initialize() {
    const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
    const connection = await this.pool.getConnection();
    try {
      for (const statement of schema.split(/;\s*(?:\n|$)/).map(x => x.trim()).filter(Boolean)) {
        await connection.query(statement);
      }
    } finally { connection.release(); }
  }

  async isEmpty() {
    const [rows] = await this.pool.query('SELECT COUNT(*) AS count FROM users');
    return Number(rows[0].count) === 0;
  }

  async read() {
    const connection = await this.pool.getConnection();
    try {
      const [[roles], [users], [farmers], [products], [indicators], [purchases], [quality], [images], [signatures]] = await Promise.all([
        connection.query('SELECT * FROM roles ORDER BY id'),
        connection.query('SELECT * FROM users ORDER BY id'),
        connection.query('SELECT * FROM farmers ORDER BY id'),
        connection.query('SELECT * FROM products ORDER BY id'),
        connection.query('SELECT * FROM quality_indicators ORDER BY id'),
        connection.query('SELECT * FROM purchases ORDER BY id'),
        connection.query('SELECT * FROM purchase_quality_values ORDER BY id'),
        connection.query('SELECT * FROM purchase_images ORDER BY id'),
        connection.query('SELECT * FROM purchase_signatures ORDER BY id')
      ]);
      const roleById = new Map(roles.map(x => [Number(x.id), x.code]));
      const qualityByPurchase = new Map();
      for (const row of quality) {
        const list = qualityByPurchase.get(Number(row.purchase_id)) || [];
        list.push({indicatorId:Number(row.indicator_id), name:row.indicator_name_snapshot, unit:row.unit_snapshot, value:row.value_text});
        qualityByPurchase.set(Number(row.purchase_id), list);
      }
      const imagesByPurchase = new Map();
      for (const row of images) {
        const list = imagesByPurchase.get(Number(row.purchase_id)) || [];
        list.push(row.object_key); imagesByPurchase.set(Number(row.purchase_id), list);
      }
      const signatureByPurchase = new Map(signatures.map(x => [Number(x.purchase_id), {dataUrl:x.object_key, signedAt:x.signed_at}]));
      return {
        organization: {id:1, code:'COMPANY', name:process.env.COMPANY_NAME || 'FieldLedger', defaultLanguage:process.env.DEFAULT_LANGUAGE || 'en'},
        users: users.map(row => ({...camel(row), id:Number(row.id), organizationId:1, role:roleById.get(Number(row.role_id))})),
        farmers: farmers.map(row => ({...camel(row), id:Number(row.id), organizationId:1, createdBy:row.created_by == null ? undefined : Number(row.created_by)})),
        products: products.map(row => ({...camel(row), id:Number(row.id), organizationId:1, active:Boolean(row.active)})),
        indicators: indicators.map(row => ({...camel(row), id:Number(row.id), productId:Number(row.product_id), required:Boolean(row.required), active:Boolean(row.active), options:jsonValue(row.options_json)})),
        purchases: purchases.map(row => ({...camel(row), id:Number(row.id), organizationId:1, farmerId:Number(row.farmer_id), productId:Number(row.product_id), createdBy:Number(row.created_by), voidedBy:row.voided_by == null ? undefined : Number(row.voided_by), qualityValues:qualityByPurchase.get(Number(row.id)) || [], images:imagesByPurchase.get(Number(row.id)) || [], signature:signatureByPurchase.get(Number(row.id)) || null}))
      };
    } finally { connection.release(); }
  }

  async write(db) {
    const connection = await this.pool.getConnection();
    try {
      await connection.beginTransaction();
      await connection.query('SET FOREIGN_KEY_CHECKS=0');
      for (const table of ['purchase_signatures','purchase_images','purchase_quality_values','purchases','quality_indicators','products','farmers','users','roles']) await connection.query(`DELETE FROM ${table}`);
      const roleCodes = [...new Set(db.users.map(x => x.role || 'OWNER'))];
      const roleIds = new Map(roleCodes.map((code, index) => [code, index + 1]));
      for (const code of roleCodes) await connection.query('INSERT INTO roles (id,code,name) VALUES (?,?,?)', [roleIds.get(code),code,code]);
      for (const u of db.users) await connection.query('INSERT INTO users (id,role_id,username,password_hash,display_name,language,status,last_login_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)', [u.id,roleIds.get(u.role || 'OWNER'),u.username,u.passwordHash,u.displayName,u.language,u.status,dateValue(u.lastLoginAt),dateValue(u.createdAt),dateValue(u.updatedAt)]);
      for (const f of db.farmers) await connection.query('INSERT INTO farmers (id,farmer_no,name,phone,village,id_number,photo_object_key,notes,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)', [f.id,f.farmerNo,f.name,f.phone || '',f.village,f.idNumber || '',nullable(f.photoObjectKey),f.notes || '',nullable(f.createdBy),dateValue(f.createdAt),dateValue(f.updatedAt)]);
      for (const p of db.products) await connection.query('INSERT INTO products (id,code,name_en,name_fr,default_unit,active,sort_order,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)', [p.id,p.code,p.nameEn,p.nameFr,p.defaultUnit,p.active,p.sortOrder || 0,dateValue(p.createdAt),dateValue(p.updatedAt)]);
      for (const i of db.indicators) await connection.query('INSERT INTO quality_indicators (id,product_id,code,name_en,name_fr,data_type,unit,required,options_json,active,sort_order,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)', [i.id,i.productId,i.code,i.nameEn,i.nameFr,i.dataType,i.unit || '',i.required,JSON.stringify(i.options || []),i.active,i.sortOrder || 0,dateValue(i.createdAt),dateValue(i.updatedAt)]);
      for (const p of db.purchases) {
        await connection.query('INSERT INTO purchases (id,purchase_no,farmer_id,product_id,purchase_date,weight,unit,bag_count,notes,status,created_by,created_at,updated_at,confirmed_at,voided_at,voided_by,void_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)', [p.id,p.purchaseNo,p.farmerId,p.productId,p.purchaseDate,p.weight,p.unit,nullable(p.bagCount),p.notes || '',p.status,p.createdBy,dateValue(p.createdAt),dateValue(p.updatedAt),dateValue(p.confirmedAt),dateValue(p.voidedAt),nullable(p.voidedBy),nullable(p.voidReason)]);
        for (const v of p.qualityValues || []) await connection.query('INSERT INTO purchase_quality_values (purchase_id,indicator_id,value_text,indicator_name_snapshot,unit_snapshot,created_at) VALUES (?,?,?,?,?,?)', [p.id,v.indicatorId,String(v.value ?? ''),v.name || '',v.unit || '',dateValue(p.createdAt)]);
        for (const [index, objectKey] of (p.images || []).entries()) await connection.query('INSERT INTO purchase_images (purchase_id,object_key,sort_order,created_at) VALUES (?,?,?,?)', [p.id,objectKey,index,dateValue(p.createdAt)]);
        if (p.signature) await connection.query('INSERT INTO purchase_signatures (purchase_id,object_key,signed_at,created_at) VALUES (?,?,?,?)', [p.id,p.signature.dataUrl || '',dateValue(p.signature.signedAt),dateValue(p.signature.signedAt)]);
      }
      await connection.query('SET FOREIGN_KEY_CHECKS=1');
      await connection.commit();
    } catch (error) {
      await connection.rollback();
      try { await connection.query('SET FOREIGN_KEY_CHECKS=1'); } catch {}
      throw error;
    } finally { connection.release(); }
  }

  async close() { await this.pool.end(); }
}

module.exports = {MysqlStore};
