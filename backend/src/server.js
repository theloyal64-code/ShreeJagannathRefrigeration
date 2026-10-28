const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
const { Pool } = require("pg");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const fs = require("node:fs");
const path = require("node:path");

const app = express();
app.disable("x-powered-by");
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGINS ? process.env.CORS_ORIGINS.split(",") : "*" }));
app.use(express.json({ limit: "32kb" }));
app.use("/api", rateLimit({ windowMs: 15 * 60 * 1000, limit: 150, standardHeaders: true, legacyHeaders: false }));

const pool = new Pool({ connectionString: process.env.DATABASE_URL, ssl: process.env.DATABASE_URL && !/localhost|127\.0\.0\.1/.test(process.env.DATABASE_URL) ? { rejectUnauthorized: false } : false });
const jwtSecret = process.env.JWT_SECRET;
if (!process.env.DATABASE_URL || !jwtSecret || jwtSecret.length < 32) {
  console.error("DATABASE_URL and a JWT_SECRET of at least 32 characters are required.");
  process.exit(1);
}

const asyncRoute = fn => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const signToken = user => jwt.sign({ sub: user.id, role: user.role, name: user.name }, jwtSecret, { expiresIn: "12h" });
function auth(...roles) {
  return (req, res, next) => {
    const token = (req.headers.authorization || "").replace(/^Bearer\s+/i, "");
    try {
      if (!token) return res.status(401).json({ error: "Authentication required" });
      req.user = jwt.verify(token, jwtSecret);
      if (roles.length && !roles.includes(req.user.role)) return res.status(403).json({ error: "Not allowed" });
      next();
    } catch { return res.status(401).json({ error: "Invalid or expired session" }); }
  };
}
const safeUser = u => ({ id: u.id, name: u.name, phone: u.phone, role: u.role, mechanicProfile: u.mechanic_profile });

async function initialize() {
  // Keep the database schema versioned in source control and safe to reapply.
  const schema = fs.readFileSync(path.join(__dirname, "schema.sql"), "utf8");
  await pool.query(schema);
}

app.get("/health", asyncRoute(async (_req, res) => {
  await pool.query("SELECT 1");
  res.json({ ok: true, service: "shree-jagannath-refrigeration-api" });
}));
app.post("/api/auth/register", asyncRoute(async (req, res) => {
  const name = String(req.body.name || "").trim();
  const phone = String(req.body.phone || "").replace(/\D/g, "");
  const password = String(req.body.password || "");
  if (name.length < 2 || phone.length < 10 || phone.length > 15 || password.length < 8) return res.status(400).json({ error: "Enter a name, valid phone number and password (8+ characters)." });
  const hash = await bcrypt.hash(password, 12);
  try {
    const { rows } = await pool.query("INSERT INTO users(name,phone,password_hash,role) VALUES($1,$2,$3,'customer') RETURNING *", [name, phone, hash]);
    res.status(201).json({ token: signToken(rows[0]), user: safeUser(rows[0]) });
  } catch (e) { if (e.code === "23505") return res.status(409).json({ error: "This phone number is already registered." }); throw e; }
}));
app.post("/api/auth/login", asyncRoute(async (req, res) => {
  const phone = String(req.body.phone || "").replace(/\D/g, "");
  const password = String(req.body.password || "");
  const { rows } = await pool.query("SELECT * FROM users WHERE phone=$1", [phone]);
  const user = rows[0];
  if (!user || !(await bcrypt.compare(password, user.password_hash))) return res.status(401).json({ error: "Phone or password is incorrect." });
  res.json({ token: signToken(user), user: safeUser(user) });
}));
app.post("/api/admin/initial-admin", asyncRoute(async (req, res) => {
  const setup = process.env.ADMIN_SETUP_TOKEN;
  if (!setup || req.get("x-admin-setup-token") !== setup) return res.status(403).json({ error: "Admin setup not authorized." });
  const name = String(req.body.name || "Administrator").trim();
  const phone = String(req.body.phone || "").replace(/\D/g, "");
  const password = String(req.body.password || "");
  if (phone.length < 10 || password.length < 12) return res.status(400).json({ error: "Admin phone and password (12+ characters) are required." });
  const hash = await bcrypt.hash(password, 12);
  const { rows } = await pool.query("INSERT INTO users(name,phone,password_hash,role) VALUES($1,$2,$3,'admin') RETURNING *", [name, phone, hash]);
  res.status(201).json({ user: safeUser(rows[0]) });
}));
app.post("/api/bookings", auth("customer"), asyncRoute(async (req, res) => {
  const b = req.body;
  const required = ["appliance","issue","address"];
  if (required.some(k => !String(b[k] || "").trim()) || !/^[0-9]{10,15}$/.test(String(b.phone || "").replace(/\D/g,""))) return res.status(400).json({ error: "Appliance, issue, address and valid phone are required." });
  const { rows } = await pool.query(
    "INSERT INTO bookings(customer_id,customer_name,phone,appliance,issue,address,preferred_slot,pincode,latitude,longitude) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *",
    [req.user.sub, String(b.customerName || req.user.name).trim(), String(b.phone).replace(/\D/g,""), String(b.appliance), String(b.issue), String(b.address), String(b.preferredSlot || ""), String(b.pincode || ""), Number.isFinite(b.latitude) ? b.latitude : null, Number.isFinite(b.longitude) ? b.longitude : null]);
  await pool.query("INSERT INTO booking_status_events(booking_id,changed_by,new_status) VALUES($1,$2,$3)", [rows[0].id, req.user.sub, rows[0].status]);
  res.status(201).json({ booking: rows[0] });
}));
app.get("/api/bookings", auth("customer","admin","mechanic"), asyncRoute(async (req, res) => {
  let result;
  if (req.user.role === "customer") result = await pool.query("SELECT * FROM bookings WHERE customer_id=$1 ORDER BY created_at DESC", [req.user.sub]);
  else if (req.user.role === "mechanic") result = await pool.query("SELECT * FROM bookings WHERE assigned_mechanic_id=$1 ORDER BY created_at DESC", [req.user.sub]);
  else result = await pool.query("SELECT * FROM bookings ORDER BY created_at DESC");
  res.json({ bookings: result.rows });
}));
app.patch("/api/bookings/:id/status", auth("admin","mechanic"), asyncRoute(async (req, res) => {
  const next = String(req.body.status || "");
  const allowed = { Pending:["Accepted","Rejected","Cancelled"], Accepted:["On the Way","Cancelled"], "On the Way":["In Progress","Cancelled"], "In Progress":["Completed","Cancelled"] };
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const found = await client.query("SELECT * FROM bookings WHERE id=$1 FOR UPDATE", [req.params.id]);
    const b = found.rows[0];
    if (!b) { await client.query("ROLLBACK"); return res.status(404).json({error:"Booking not found"}); }
    if (req.user.role === "mechanic" && String(b.assigned_mechanic_id) !== String(req.user.sub)) { await client.query("ROLLBACK"); return res.status(403).json({error:"Booking is not assigned to you"}); }
    if (!(allowed[b.status] || []).includes(next)) { await client.query("ROLLBACK"); return res.status(409).json({error:"Invalid status transition"}); }
    const updated = await client.query("UPDATE bookings SET status=$1,status_updated_at=now() WHERE id=$2 RETURNING *", [next,b.id]);
    await client.query("INSERT INTO booking_status_events(booking_id,changed_by,old_status,new_status) VALUES($1,$2,$3,$4)", [b.id,req.user.sub,b.status,next]);
    await client.query("COMMIT");
    res.json({booking:updated.rows[0]});
  } catch(e) { await client.query("ROLLBACK"); throw e; } finally { client.release(); }
}));
app.patch("/api/bookings/:id/assignment", auth("admin"), asyncRoute(async (req,res) => {
  const mechanicId = Number(req.body.mechanicId);
  const check = await pool.query("SELECT id FROM users WHERE id=$1 AND role='mechanic'", [mechanicId]);
  if (!check.rowCount) return res.status(400).json({error:"Valid mechanic account required"});
  const {rows} = await pool.query("UPDATE bookings SET assigned_mechanic_id=$1 WHERE id=$2 RETURNING *",[mechanicId,req.params.id]);
  if (!rows[0]) return res.status(404).json({error:"Booking not found"});
  res.json({booking:rows[0]});
}));
app.post("/api/bookings/:id/review", auth("customer"), asyncRoute(async (req,res) => {
  const rating = Number(req.body.rating);
  const review = String(req.body.review || "").trim();
  if (!Number.isInteger(rating) || rating < 1 || rating > 5 || review.length > 500) return res.status(400).json({error:"Rating must be 1–5 stars and review must be under 500 characters."});
  const {rows} = await pool.query("UPDATE bookings SET rating=$1,review=$2,reviewed_at=now() WHERE id=$3 AND customer_id=$4 AND status='Completed' RETURNING *",[rating,review,req.params.id,req.user.sub]);
  if (!rows[0]) return res.status(404).json({error:"Completed booking not found for this customer"});
  res.json({booking:rows[0]});
}));
app.get("/api/reviews", auth("admin"), asyncRoute(async (_req,res) => {
  const {rows} = await pool.query("SELECT id,customer_name,appliance,rating,review,reviewed_at FROM bookings WHERE rating IS NOT NULL ORDER BY reviewed_at DESC");
  res.json({reviews:rows});
}));
app.get("/api/admin/mechanics", auth("admin"), asyncRoute(async (_req,res) => {
  const {rows} = await pool.query("SELECT id,name,phone,mechanic_profile FROM users WHERE role='mechanic' ORDER BY name");
  res.json({mechanics:rows});
}));
app.post("/api/admin/mechanics", auth("admin"), asyncRoute(async (req,res) => {
  const name=String(req.body.name||"").trim(), phone=String(req.body.phone||"").replace(/\D/g,""), password=String(req.body.password||""), profile=String(req.body.profile||"Mechanic");
  if(name.length<2||phone.length<10||password.length<8) return res.status(400).json({error:"Name, valid phone and password (8+ characters) are required"});
  const hash=await bcrypt.hash(password,12);
  try { const {rows}=await pool.query("INSERT INTO users(name,phone,password_hash,role,mechanic_profile) VALUES($1,$2,$3,'mechanic',$4) RETURNING *",[name,phone,hash,profile]); res.status(201).json({mechanic:safeUser(rows[0])}); }
  catch(e){ if(e.code==="23505") return res.status(409).json({error:"Phone number already exists"}); throw e; }
}));
app.use((err,_req,res,_next) => { console.error(err); res.status(500).json({error:"Internal server error"}); });
initialize().then(() => {
  const port = Number(process.env.PORT || 8080);
  app.listen(port, "0.0.0.0", () => console.log(`API listening on ${port}`));
}).catch(err => { console.error("Database initialization failed", err); process.exit(1); });
