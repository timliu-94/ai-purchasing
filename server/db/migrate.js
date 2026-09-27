const fs = require('fs');
const path = require('path');
const {MysqlStore} = require('./mysql-store');

async function main() {
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error('DATABASE_URL is required');
  const source = process.env.FIELDLEDGER_DB || path.join(__dirname, '..', 'data', 'database.json');
  const data = JSON.parse(fs.readFileSync(source, 'utf8'));
  const store = new MysqlStore(url);
  try {
    await store.initialize();
    if (!(await store.isEmpty()) && process.env.ALLOW_DATABASE_REPLACE !== 'yes') {
      throw new Error('Target database is not empty. Set ALLOW_DATABASE_REPLACE=yes to replace its FieldLedger data.');
    }
    await store.write(data);
    const migrated = await store.read();
    console.log(`Migrated ${migrated.farmers.length} farmers, ${migrated.products.length} products and ${migrated.purchases.length} purchases.`);
  } finally { await store.close(); }
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
