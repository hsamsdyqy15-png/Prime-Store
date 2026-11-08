import { createClient } from '@supabase/supabase-js';
export const BUCKET = 'store-files';
export const ADMIN_EMAILS = ['toprsidar2@gmail.com', 'zaim9002@gmail.com', 'ضع_بريدك_هنا'];
export const supabase = createClient(
  'https://koloemxbxrenwqiajijo.supabase.co',
  'sb_publishable_Ol48kUBezAiwHSYhutTWbQ_ghTohVo6',
  { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: false, storageKey: 'ps-auth' } }
);
export const isAdmin = (email?: string | null) =>
  !!email && ADMIN_EMAILS.map(e => e.toLowerCase()).includes(email.toLowerCase());

export async function restoreSession() {
  const { data } = await supabase.auth.getSession();
  if (data.session) return data.session;
  const rt = localStorage.getItem('ps_rt');
  if (rt) {
    const r = await supabase.auth.refreshSession({ refresh_token: rt });
    return r.data.session ?? null;
  }
  return null;
}
supabase.auth.onAuthStateChange((_e, s) => {
  if (s?.refresh_token) localStorage.setItem('ps_rt', s.refresh_token);
});
export async function signOutAll() {
  await supabase.auth.signOut();
  localStorage.removeItem('ps_rt');
}
export async function uploadFile(file: File, folder: string) {
  const ext = file.name.split('.').pop();
  const path = `${folder}/${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${ext}`;
  const { error } = await supabase.storage.from(BUCKET).upload(path, file);
  if (error) throw error;
  return supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl;
}
