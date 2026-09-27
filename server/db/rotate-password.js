const fs = require('fs');
const crypto = require('crypto');
const mysql = require('mysql2/promise');

async function main() {
  if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
  const username = process.argv[2] || 'owner';
  const output = process.argv[3];
  const password = crypto.randomBytes(18).toString('base64url');
  const passwordHash = crypto.createHash('sha256').update(password).digest('hex');
  const connection = await mysql.createConnection(process.env.DATABASE_URL);
  try {
    const [result] = await connection.execute(
      'UPDATE users SET password_hash=?, updated_at=NOW(3) WHERE username=?',
      [passwordHash, username]
    );
    if (result.affectedRows !== 1) throw new Error(`Expected one user named ${username}`);
  } finally { await connection.end(); }
  if (output) {
    fs.writeFileSync(output, `username=${username}\npassword=${password}\n`, {mode:0o600});
    console.log(`Password rotated; credentials written to ${output}`);
  } else {
    console.log(password);
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
