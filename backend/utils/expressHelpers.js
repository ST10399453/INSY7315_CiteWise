import { validationResult } from "express-validator";

export function bailIfInvalid(req, res) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ errors: errors.array() });
}

export function isAdmin(user) {
  return Boolean(user?.claims?.role === "admin" || user?.claims?.admin === true);
}

export function canAccessRequest(doc, user) {
  if (!doc || !user) return false;
  const isOwner = doc.userId === user.uid;
  const isConsultant = doc.consultantId && doc.consultantId === user.uid;
  const isAdminUser = isAdmin(user);
  return isOwner || isConsultant || isAdminUser;
}
