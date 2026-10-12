import React, { useState, useEffect, useRef } from 'react';
import {
  FileText, CheckCircle2, Loader2, RefreshCw, ShieldCheck, FileUp, X,
  AlertCircle, FileCode, ExternalLink, Download, MessageSquare, Send,
  Sparkles, Upload, Bot, User, AlertTriangle, CheckCheck, ArrowLeft,
  LayoutDashboard, Plus, Clock, Eye, Search, Trash2
} from 'lucide-react';

const STATUS_STYLE = {
  DRAFT:    { badge: 'bg-amber-50 text-amber-700 border border-amber-200', dot: 'bg-amber-400' },
  REVIEW:   { badge: 'bg-blue-50 text-blue-700 border border-blue-200',   dot: 'bg-blue-400'   },
  APPROVED: { badge: 'bg-green-50 text-green-700 border border-green-200', dot: 'bg-green-500' },
};
const PERMITTED = { DRAFT: ['REVIEW'], REVIEW: ['DRAFT', 'APPROVED'], APPROVED: ['REVIEW'] };
const TRANSITION_LABEL = {
  'DRAFT_REVIEW':'Submit for Review','REVIEW_APPROVED':'Approve',
  'REVIEW_DRAFT':'Revert to Draft','APPROVED_REVIEW':'Return to Review',
};
const SUGGESTED = [
  'What is the duration of this contract?',
  'When must payment be made?',
  'What are the termination conditions?',
  'Who are the parties involved?',
  'What are the confidentiality obligations?',
  'What is the liability clause?',
];

function fmt(dt) {
  if (!dt) return '-';
  return new Date(dt).toLocaleString('en-IN',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'});
}

export default function App() {
  const [health, setHealth]         = useState({ loading: true, ok: false });
  const [contracts, setContracts]   = useState([]);
  const [loading, setLoading]       = useState(false);
  const [toast, setToast]           = useState(null);
  const [active, setActive]         = useState(null);
  const [updatingId, setUpdatingId] = useState(null);
  const [search, setSearch]         = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [uploadOpen, setUploadOpen] = useState(false);
  const [title, setTitle]           = useState('');
  const [file, setFile]             = useState(null);
  const [uploading, setUploading]   = useState(false);
  const [uploadErr, setUploadErr]   = useState(null);
  const [uploadDone, setUploadDone] = useState(null);
  const [qaHistory, setQaHistory]   = useState([]);
  const [qaInput, setQaInput]       = useState('');
  const [qaLoading, setQaLoading]   = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting]         = useState(false);
  const chatEnd = useRef(null);

  useEffect(() => { checkHealth(); fetchContracts(); }, []);
  useEffect(() => { chatEnd.current?.scrollIntoView({ behavior: 'smooth' }); }, [qaHistory]);
  useEffect(() => { setQaHistory([]); setQaInput(''); }, [active?.id]);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  async function checkHealth() {
    setHealth({ loading: true, ok: false });
    try { const r = await fetch('/api/health'); const d = await r.json(); setHealth({ loading: false, ok: d?.status === 'UP' }); }
    catch { setHealth({ loading: false, ok: false }); }
  }
  async function fetchContracts() {
    setLoading(true);
    try { const r = await fetch('/api/contracts'); if (r.ok) setContracts(await r.json()); }
    catch {} finally { setLoading(false); }
  }
  async function handleStatusChange(contractId, newStatus) {
    if (updatingId) return;
    setUpdatingId(contractId);
    try {
      const r = await fetch(`/api/contracts/${contractId}/status`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus }),
      });
      const d = await r.json();
      if (!r.ok) throw new Error(d.message || 'Failed');
      setToast({ type: 'success', msg: `Status updated to ${newStatus}` });
      await fetchContracts();
      if (active?.id === contractId) setActive(prev => ({ ...prev, status: newStatus }));
    } catch (e) { setToast({ type: 'error', msg: e.message }); }
    finally { setUpdatingId(null); }
  }
  async function handleDeleteContract(contractId) {
    if (deleting) return;
    setDeleting(true);
    try {
      const r = await fetch(`/api/contracts/${contractId}`, { method: 'DELETE' });
      if (!r.ok && r.status !== 204) {
        const d = await r.json().catch(() => ({}));
        throw new Error(d.message || 'Failed to delete contract');
      }
      setToast({ type: 'success', msg: 'Contract deleted successfully' });
      if (active?.id === contractId) setActive(null);
      setDeleteTarget(null);
      await fetchContracts();
    } catch (e) {
      setToast({ type: 'error', msg: e.message });
    } finally {
      setDeleting(false);
    }
  }
  async function handleUpload(e) {
    e.preventDefault();
    if (!title.trim() || !file) return;
    setUploading(true); setUploadErr(null);
    try {
      const fd = new FormData();
      fd.append('title', title.trim()); fd.append('file', file);
      const r = await fetch('/api/contracts', { method: 'POST', body: fd });
      const d = await r.json();
      if (!r.ok) throw new Error(d.message || 'Upload failed');
      setUploadDone(d); setTitle(''); setFile(null);
      await fetchContracts();
    } catch (e) { setUploadErr(e.message); }
    finally { setUploading(false); }
  }
  function handleFileSelect(f) {
    if (!f) return;
    const ext = f.name.split('.').pop().toLowerCase();
    if (!['pdf','docx','doc'].includes(ext)) { setUploadErr('Only PDF, DOCX, or DOC files are supported.'); return; }
    setFile(f); setUploadErr(null);
    if (!title.trim()) {
      const base = f.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');
      setTitle(base.charAt(0).toUpperCase() + base.slice(1));
    }
  }
  async function handleAsk(question) {
    const q = (question || qaInput).trim();
    if (!q || !active || qaLoading) return;
    setQaInput(''); setQaLoading(true);
    setQaHistory(h => [...h, { role: 'user', text: q }]);
    try {
      const r = await fetch(`/api/contracts/${active.id}/ask`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: q }),
      });
      const d = await r.json();
      if (!r.ok) throw new Error(d.message || 'Error');
      setQaHistory(h => [...h, { role: 'assistant', text: d.answer, found: d.found, excerpt: d.sourceExcerpt }]);
    } catch (e) { setQaHistory(h => [...h, { role: 'error', text: e.message }]); }
    finally { setQaLoading(false); }
  }
  function closeModal() {
    if (uploading) return;
    setUploadOpen(false); setUploadErr(null); setUploadDone(null); setTitle(''); setFile(null);
  }

  const noText = active && active.originalFilename && !active.extractedText;
  const visible = contracts.filter(c => {
    const ms = !search || c.title.toLowerCase().includes(search.toLowerCase()) || (c.originalFilename||'').toLowerCase().includes(search.toLowerCase());
    const mf = statusFilter === 'ALL' || c.status === statusFilter;
    return ms && mf;
  });
  const counts = { total: contracts.length, DRAFT: 0, REVIEW: 0, APPROVED: 0 };
  contracts.forEach(c => { counts[c.status] = (counts[c.status]||0)+1; });

  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden" style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      {/* SIDEBAR */}
      <aside className="w-56 bg-gray-900 flex flex-col shrink-0">
        <div className="px-5 py-5 border-b border-gray-800">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center">
              <ShieldCheck className="w-4 h-4 text-white"/>
            </div>
            <div>
              <p className="text-sm font-bold text-white">ContractVault</p>
              <p className="text-[10px] text-gray-500">Internship Project</p>
            </div>
          </div>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
          <p className="text-[10px] font-semibold text-gray-600 uppercase tracking-wider px-2 mb-2">Main</p>
          <button onClick={() => setActive(null)} className={`w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm transition-colors ${!active?'bg-indigo-600 text-white':'text-gray-400 hover:text-white hover:bg-gray-800'}`}>
            <LayoutDashboard className="w-4 h-4 shrink-0"/><span>Dashboard</span>
            {contracts.length>0&&<span className="ml-auto text-[10px] bg-gray-700 text-gray-300 rounded-full px-1.5 py-0.5">{contracts.length}</span>}
          </button>
          <button onClick={() => setUploadOpen(true)} className="w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm text-gray-400 hover:text-white hover:bg-gray-800 transition-colors">
            <FileUp className="w-4 h-4 shrink-0"/><span>Upload</span>
          </button>
          {active && (
            <>
              <p className="text-[10px] font-semibold text-gray-600 uppercase tracking-wider px-2 mt-4 mb-2">Selected</p>
              <div className="px-2.5 py-2 rounded-lg bg-gray-800 text-xs">
                <p className="text-white font-medium truncate">{active.title}</p>
                <p className="text-gray-500 mt-0.5 truncate">{active.originalFilename||'No file'}</p>
                <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-1.5 py-0.5 rounded mt-1.5 ${STATUS_STYLE[active.status]?.badge}`}>
                  {active.status}
                </span>
              </div>
            </>
          )}
          <p className="text-[10px] font-semibold text-gray-600 uppercase tracking-wider px-2 mt-4 mb-2">Filter</p>
          {['ALL','DRAFT','REVIEW','APPROVED'].map(s => (
            <button key={s} onClick={() => setStatusFilter(s)} className={`w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg text-xs transition-colors ${statusFilter===s?'bg-gray-800 text-white':'text-gray-500 hover:text-gray-300 hover:bg-gray-800'}`}>
              <span>{s==='ALL'?'All':s.charAt(0)+s.slice(1).toLowerCase()}</span>
              <span className="text-gray-600">{s==='ALL'?counts.total:counts[s]||0}</span>
            </button>
          ))}
        </nav>
        <div className="px-4 py-3 border-t border-gray-800">
          <div className={`flex items-center gap-1.5 text-[11px] ${health.ok?'text-green-400':'text-red-400'}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${health.ok?'bg-green-400 animate-pulse':'bg-red-500'}`}/>
            {health.loading?'Connecting…':health.ok?'API Online':'API Offline'}
          </div>
        </div>
      </aside>

      {/* MAIN */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="bg-white border-b border-gray-200 px-6 py-3.5 flex items-center justify-between shrink-0">
          <div>
            <h1 className="text-base font-bold text-gray-900">{active?'Contract Q&A':'My Contracts'}</h1>
            <p className="text-xs text-gray-500 mt-0.5">{active?`${active.title} · #${active.id}`:`${visible.length} of ${contracts.length} records`}</p>
          </div>
          <div className="flex items-center gap-2">
            {active && (
              <button onClick={() => setActive(null)} className="flex items-center gap-1.5 text-xs text-gray-600 hover:text-gray-900 px-3 py-1.5 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors">
                <ArrowLeft className="w-3.5 h-3.5"/> Back
              </button>
            )}
            <button onClick={() => { checkHealth(); fetchContracts(); }} className="p-2 rounded-lg text-gray-500 hover:text-gray-900 hover:bg-gray-100 transition-colors">
              <RefreshCw className={`w-4 h-4 ${loading?'animate-spin':''}`}/>
            </button>
            <button onClick={() => setUploadOpen(true)} className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition-colors shadow-sm">
              <Plus className="w-3.5 h-3.5"/> Upload Contract
            </button>
          </div>
        </header>

        {toast && (
          <div className={`mx-6 mt-3 shrink-0 flex items-center justify-between px-4 py-2.5 rounded-xl text-sm font-medium shadow-sm ${toast.type==='success'?'bg-green-50 text-green-800 border border-green-200':'bg-red-50 text-red-800 border border-red-200'}`}>
            <div className="flex items-center gap-2">
              {toast.type==='success'?<CheckCircle2 className="w-4 h-4 text-green-600"/>:<AlertCircle className="w-4 h-4 text-red-500"/>}
              {toast.msg}
            </div>
            <button onClick={() => setToast(null)}><X className="w-4 h-4 opacity-60 hover:opacity-100"/></button>
          </div>
        )}

        {/* DASHBOARD */}
        {!active && (
          <div className="flex-1 overflow-auto px-6 py-4">
            <div className="grid grid-cols-4 gap-4 mb-5">
              {[
                {label:'Total',value:counts.total,icon:FileText,color:'text-indigo-600 bg-indigo-50'},
                {label:'Draft',value:counts.DRAFT,icon:Clock,color:'text-amber-600 bg-amber-50'},
                {label:'In Review',value:counts.REVIEW,icon:Eye,color:'text-blue-600 bg-blue-50'},
                {label:'Approved',value:counts.APPROVED,icon:CheckCircle2,color:'text-green-600 bg-green-50'},
              ].map(s => (
                <div key={s.label} className="bg-white rounded-xl border border-gray-200 px-5 py-4 flex items-center gap-4 shadow-sm">
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${s.color}`}><s.icon className="w-5 h-5"/></div>
                  <div><p className="text-2xl font-bold text-gray-900">{s.value}</p><p className="text-xs text-gray-500">{s.label}</p></div>
                </div>
              ))}
            </div>
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
              <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100">
                <div className="relative flex-1 max-w-xs">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400"/>
                  <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search contracts…"
                    className="w-full pl-8 pr-3 py-1.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"/>
                </div>
                <div className="flex items-center gap-1">
                  {['ALL','DRAFT','REVIEW','APPROVED'].map(s => (
                    <button key={s} onClick={() => setStatusFilter(s)}
                      className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${statusFilter===s?'bg-indigo-600 text-white':'text-gray-600 hover:bg-gray-100'}`}>
                      {s==='ALL'?'All':s.charAt(0)+s.slice(1).toLowerCase()}
                    </button>
                  ))}
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 border-b border-gray-100">
                      {['ID','Title','File','Uploaded','Status','Actions'].map(h => (
                        <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {loading && visible.length===0 ? (
                      <tr><td colSpan={6} className="text-center py-16 text-gray-400">
                        <Loader2 className="w-5 h-5 animate-spin mx-auto mb-2"/>
                        <p className="text-xs">Loading contracts…</p>
                      </td></tr>
                    ) : visible.length===0 ? (
                      <tr><td colSpan={6} className="text-center py-16">
                        <FileText className="w-10 h-10 mx-auto mb-2 text-gray-300"/>
                        <p className="text-sm font-medium text-gray-500">{search||statusFilter!=='ALL'?'No contracts match your filter':'No contracts yet'}</p>
                        {!search&&statusFilter==='ALL'&&<button onClick={() => setUploadOpen(true)} className="mt-3 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700">Upload First Contract</button>}
                      </td></tr>
                    ) : visible.map(c => (
                      <tr key={c.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3"><span className="text-xs font-mono text-gray-400">#{c.id}</span></td>
                        <td className="px-4 py-3">
                          <button onClick={() => setActive(c)} className="text-sm font-medium text-gray-900 hover:text-indigo-600 transition-colors text-left">{c.title}</button>
                        </td>
                        <td className="px-4 py-3">
                          {c.originalFilename ? (
                            <a href={`/api/contracts/${c.id}/document`}
                              target={c.originalFilename.endsWith('.pdf')?'_blank':undefined}
                              download={!c.originalFilename.endsWith('.pdf')?c.originalFilename:undefined}
                              rel="noopener noreferrer"
                              className="flex items-center gap-1.5 text-xs text-indigo-600 hover:text-indigo-800 transition-colors">
                              <FileCode className="w-3.5 h-3.5 shrink-0"/>
                              <span className="truncate max-w-[140px]">{c.originalFilename}</span>
                              {c.originalFilename.endsWith('.pdf')?<ExternalLink className="w-3 h-3"/>:<Download className="w-3 h-3"/>}
                            </a>
                          ) : <span className="text-xs text-gray-400">No file</span>}
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{fmt(c.createdAt)}</td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full ${STATUS_STYLE[c.status]?.badge}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${STATUS_STYLE[c.status]?.dot}`}/>
                            {c.status.charAt(0)+c.status.slice(1).toLowerCase()}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <button onClick={() => setActive(c)} className="flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 px-2 py-1 rounded-lg hover:bg-indigo-50 transition-colors font-medium">
                              <MessageSquare className="w-3.5 h-3.5"/> Ask Q&A
                            </button>
                            {PERMITTED[c.status]?.map(t => (
                              <button key={t} onClick={() => handleStatusChange(c.id, t)} disabled={updatingId===c.id}
                                className="text-[11px] px-2 py-1 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-100 disabled:opacity-40 transition-colors whitespace-nowrap flex items-center gap-1">
                                {updatingId===c.id&&<Loader2 className="w-2.5 h-2.5 animate-spin"/>}
                                {TRANSITION_LABEL[`${c.status}_${t}`]||t}
                              </button>
                            ))}
                            <button onClick={() => setDeleteTarget(c)} title="Delete contract"
                              className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors ml-1">
                              <Trash2 className="w-3.5 h-3.5"/>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Q&A PANEL */}
        {active && (
          <div className="flex-1 flex overflow-hidden">
            <div className="w-60 border-r border-gray-200 bg-white flex flex-col shrink-0">
              <div className="p-4 border-b border-gray-100">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Contract Details</p>
                <div className="space-y-2 text-xs">
                  <div><p className="text-gray-400">ID</p><p className="font-mono font-bold text-gray-900">#{active.id}</p></div>
                  <div><p className="text-gray-400">Title</p><p className="font-semibold text-gray-900 break-words">{active.title}</p></div>
                  <div><p className="text-gray-400">File</p>
                    {active.originalFilename ? (
                      <a href={`/api/contracts/${active.id}/document`}
                        target={active.originalFilename.endsWith('.pdf')?'_blank':undefined}
                        download={!active.originalFilename.endsWith('.pdf')?active.originalFilename:undefined}
                        className="text-indigo-600 hover:underline flex items-center gap-1 break-all">
                        {active.originalFilename}
                        {active.originalFilename.endsWith('.pdf')?<ExternalLink className="w-2.5 h-2.5 shrink-0"/>:<Download className="w-2.5 h-2.5 shrink-0"/>}
                      </a>
                    ) : <span className="text-gray-400">None</span>}
                  </div>
                  <div><p className="text-gray-400">Uploaded</p><p>{fmt(active.createdAt)}</p></div>
                  <div><p className="text-gray-400">Status</p>
                    <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full mt-0.5 ${STATUS_STYLE[active.status]?.badge}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${STATUS_STYLE[active.status]?.dot}`}/>{active.status}
                    </span>
                  </div>
                </div>
              </div>
              <div className="p-4">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Change Status</p>
                <div className="space-y-1.5">
                  {PERMITTED[active.status]?.map(t => (
                    <button key={t} onClick={() => handleStatusChange(active.id, t)} disabled={!!updatingId}
                      className="w-full text-xs px-3 py-2 rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-50 disabled:opacity-40 transition-colors text-left flex items-center gap-1.5">
                      {updatingId===active.id&&<Loader2 className="w-3 h-3 animate-spin"/>}
                      {TRANSITION_LABEL[`${active.status}_${t}`]||t}
                    </button>
                  ))}
                </div>
              </div>
              <div className="p-4 border-t border-gray-100 mt-auto">
                <button onClick={() => setDeleteTarget(active)}
                  className="w-full text-xs px-3 py-2 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 hover:border-red-300 font-medium transition-colors flex items-center justify-center gap-1.5">
                  <Trash2 className="w-3.5 h-3.5"/> Delete Contract
                </button>
              </div>
            </div>
            <div className="flex-1 flex flex-col bg-gray-50">
              {noText && (
                <div className="mx-5 mt-4 flex items-start gap-2.5 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 shrink-0">
                  <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5"/>
                  <div className="text-xs text-amber-800">
                    <p className="font-semibold mb-0.5">No text extracted</p>
                    This appears to be a scanned/image PDF. Q&A requires a text-based PDF, DOCX, or DOC file.
                  </div>
                </div>
              )}
              <div className="flex-1 overflow-y-auto px-5 py-5 space-y-4">
                {qaHistory.length===0 && (
                  <div className="flex flex-col items-center text-center py-10">
                    <div className="w-14 h-14 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center mb-4">
                      <Sparkles className="w-7 h-7 text-indigo-500"/>
                    </div>
                    <h2 className="text-base font-bold text-gray-900 mb-1">Ask anything about this contract</h2>
                    <p className="text-xs text-gray-500 max-w-xs mb-6">Keywords from your question are matched against the extracted document text to find the most relevant passage.</p>
                    {!noText && (
                      <div className="flex flex-wrap gap-2 justify-center max-w-lg">
                        {SUGGESTED.map(q => (
                          <button key={q} onClick={() => handleAsk(q)}
                            className="text-xs px-3 py-1.5 rounded-full bg-white border border-gray-200 text-gray-600 hover:border-indigo-400 hover:text-indigo-600 hover:bg-indigo-50 transition-all shadow-sm">
                            {q}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
                {qaHistory.map((msg, i) => (
                  <div key={i} className={`flex gap-3 ${msg.role==='user'?'justify-end':'justify-start'}`}>
                    {msg.role!=='user' && (
                      <div className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${msg.role==='error'?'bg-red-500':'bg-indigo-600'}`}>
                        <Bot className="w-4 h-4 text-white"/>
                      </div>
                    )}
                    <div className="max-w-[78%]">
                      {msg.role==='user' && <div className="bg-indigo-600 text-white px-4 py-2.5 rounded-2xl rounded-tr-sm text-sm shadow-sm">{msg.text}</div>}
                      {msg.role==='assistant' && (
                        <div className={`px-4 py-3 rounded-2xl rounded-tl-sm text-sm border shadow-sm ${msg.found?'bg-white border-gray-200 text-gray-800':'bg-amber-50 border-amber-200 text-amber-900'}`}>
                          {!msg.found&&<AlertTriangle className="w-3.5 h-3.5 text-amber-500 inline mr-1.5 mb-0.5"/>}
                          <span>{msg.text}</span>
                          {msg.found&&msg.excerpt && (
                            <div className="mt-2.5 pt-2.5 border-t border-gray-100">
                              <p className="text-[10px] uppercase tracking-wider text-indigo-500 font-semibold mb-1.5">Source passage</p>
                              <blockquote className="text-xs italic text-gray-600 bg-gray-50 rounded-lg px-3 py-2 border-l-2 border-indigo-400">"{msg.excerpt}"</blockquote>
                            </div>
                          )}
                        </div>
                      )}
                      {msg.role==='error' && (
                        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-2.5 rounded-2xl rounded-tl-sm text-sm flex items-center gap-2">
                          <AlertCircle className="w-4 h-4 shrink-0"/>{msg.text}
                        </div>
                      )}
                    </div>
                    {msg.role==='user' && (
                      <div className="w-7 h-7 rounded-full bg-indigo-100 flex items-center justify-center shrink-0 mt-0.5">
                        <User className="w-4 h-4 text-indigo-600"/>
                      </div>
                    )}
                  </div>
                ))}
                {qaLoading && (
                  <div className="flex gap-3">
                    <div className="w-7 h-7 rounded-full bg-indigo-600 flex items-center justify-center shrink-0"><Bot className="w-4 h-4 text-white"/></div>
                    <div className="bg-white border border-gray-200 px-4 py-3 rounded-2xl rounded-tl-sm flex items-center gap-1.5 shadow-sm">
                      {[0,150,300].map(d=><span key={d} className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce" style={{animationDelay:`${d}ms`}}/>)}
                    </div>
                  </div>
                )}
                <div ref={chatEnd}/>
              </div>
              <div className="shrink-0 border-t border-gray-200 bg-white px-5 py-4">
                {noText ? (
                  <div className="flex items-center gap-2 text-xs text-amber-600 justify-center py-1">
                    <AlertTriangle className="w-3.5 h-3.5"/>Q&A unavailable — no text could be extracted from this document
                  </div>
                ) : (
                  <form onSubmit={e=>{e.preventDefault();handleAsk();}} className="flex gap-2">
                    <input value={qaInput} onChange={e=>setQaInput(e.target.value)}
                      placeholder={`Ask a question about "${active.title}"…`}
                      disabled={qaLoading} maxLength={500}
                      className="flex-1 border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50 bg-gray-50"/>
                    <button type="submit" disabled={qaLoading||!qaInput.trim()}
                      className="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm disabled:opacity-40 transition-colors flex items-center gap-1.5 shadow-sm">
                      {qaLoading?<Loader2 className="w-4 h-4 animate-spin"/>:<Send className="w-4 h-4"/>}
                    </button>
                  </form>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* UPLOAD MODAL */}
      {uploadOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={e=>{if(e.target===e.currentTarget)closeModal();}}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-indigo-50 border border-indigo-100 flex items-center justify-center"><FileUp className="w-4 h-4 text-indigo-600"/></div>
                <h2 className="text-base font-bold text-gray-900">Upload Contract</h2>
              </div>
              <button onClick={closeModal} disabled={uploading} className="p-1.5 rounded-lg text-gray-400 hover:text-gray-900 hover:bg-gray-100"><X className="w-5 h-5"/></button>
            </div>
            {uploadDone ? (
              <div className="space-y-4">
                <div className="bg-green-50 border border-green-200 rounded-xl p-4">
                  <div className="flex items-center gap-2 mb-1.5"><CheckCheck className="w-5 h-5 text-green-600"/><span className="text-sm font-semibold text-green-800">Uploaded Successfully</span></div>
                  <p className="text-xs text-green-700">#{uploadDone.id} · {uploadDone.title}</p>
                  {!uploadDone.extractedText && (
                    <div className="mt-2.5 flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg p-2.5">
                      <AlertTriangle className="w-3.5 h-3.5 text-amber-500 shrink-0 mt-0.5"/>
                      <p className="text-[11px] text-amber-800">No text was extracted — likely a scanned/image PDF. File saved, but Q&A won't be available. Re-upload as text-based PDF, DOCX, or DOC.</p>
                    </div>
                  )}
                </div>
                <div className="flex gap-3 justify-end">
                  <button onClick={() => setUploadDone(null)} className="px-4 py-2 rounded-xl text-xs font-medium border border-gray-200 text-gray-700 hover:bg-gray-50">Upload Another</button>
                  <button onClick={closeModal} className="px-4 py-2 rounded-xl text-xs font-semibold bg-indigo-600 text-white hover:bg-indigo-700">Done</button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleUpload} className="space-y-4">
                {uploadErr && (
                  <div className="flex items-start gap-2 bg-red-50 border border-red-200 rounded-xl px-3 py-2.5 text-xs text-red-700">
                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-red-500"/>{uploadErr}
                  </div>
                )}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Contract Title <span className="text-red-500">*</span></label>
                  <input value={title} onChange={e=>setTitle(e.target.value)} placeholder="e.g., Supplier Master Agreement 2026"
                    disabled={uploading} className="w-full border border-gray-200 rounded-xl px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"/>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">
                    Document File <span className="text-red-500">*</span>
                    <span className="ml-1.5 text-[10px] normal-case text-gray-400 font-normal">PDF, DOCX, or DOC · max 10 MB</span>
                  </label>
                  <label className={`block cursor-pointer border-2 border-dashed rounded-xl p-5 text-center transition-all ${file?'border-indigo-400 bg-indigo-50':'border-gray-200 hover:border-indigo-300 hover:bg-gray-50'}`}>
                    <input type="file" accept=".pdf,.docx,.doc" className="sr-only" disabled={uploading} onChange={e=>handleFileSelect(e.target.files?.[0])}/>
                    {file ? (
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                          <FileCode className="w-5 h-5 text-indigo-500"/>
                          <div className="text-left"><p className="text-sm font-medium text-gray-900">{file.name}</p><p className="text-xs text-gray-500">{(file.size/1024).toFixed(1)} KB</p></div>
                        </div>
                        <button type="button" onClick={e=>{e.preventDefault();setFile(null);}} className="p-1 text-gray-400 hover:text-red-500"><X className="w-4 h-4"/></button>
                      </div>
                    ) : (
                      <>
                        <FileUp className="w-8 h-8 text-gray-300 mx-auto mb-2"/>
                        <p className="text-sm text-gray-500"><span className="text-indigo-600 font-medium">Click to choose</span> or drag a file</p>
                        <p className="text-xs text-gray-400 mt-1">Text-based PDF recommended for Q&A</p>
                      </>
                    )}
                  </label>
                </div>
                <div className="flex justify-end gap-3 pt-1">
                  <button type="button" onClick={closeModal} disabled={uploading} className="px-4 py-2.5 rounded-xl text-xs font-medium text-gray-600 hover:bg-gray-100">Cancel</button>
                  <button type="submit" disabled={uploading||!file||!title.trim()}
                    className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold disabled:opacity-40 transition-colors shadow-sm">
                    {uploading?<><Loader2 className="w-3.5 h-3.5 animate-spin"/><span>Uploading…</span></>:<><Upload className="w-3.5 h-3.5"/><span>Upload &amp; Extract Text</span></>}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 shadow-xl border border-gray-100">
            <div className="w-11 h-11 rounded-full bg-red-100 text-red-600 flex items-center justify-center mb-4">
              <Trash2 className="w-5 h-5" />
            </div>
            <h3 className="text-base font-bold text-gray-900 mb-1">Delete Contract?</h3>
            <p className="text-xs text-gray-500 mb-5 leading-relaxed">
              Are you sure you want to permanently delete <span className="font-semibold text-gray-800">"{deleteTarget.title}"</span>? This will remove the contract record and delete any uploaded document from server storage.
            </p>
            <div className="flex items-center justify-end gap-2.5">
              <button
                onClick={() => setDeleteTarget(null)}
                disabled={deleting}
                className="px-3.5 py-2 text-xs font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => handleDeleteContract(deleteTarget.id)}
                disabled={deleting}
                className="px-4 py-2 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 rounded-lg transition-colors flex items-center gap-1.5 shadow-sm"
              >
                {deleting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
