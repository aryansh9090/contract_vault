import React, { useState, useEffect, useRef } from 'react';
import {
  FileText, CheckCircle2, Loader2, RefreshCw, ShieldCheck, FileUp, X,
  AlertCircle, FileCode, ExternalLink, Download, MessageSquare, Send,
  Sparkles, Upload, Bot, User, AlertTriangle, CheckCheck, ArrowLeft,
  LayoutDashboard, Plus, Clock, Eye, Search, Trash2, Menu, ChevronDown, ChevronUp, Info
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
  
  // Mobile responsive states
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [mobileDetailsOpen, setMobileDetailsOpen] = useState(false);
  const chatEnd = useRef(null);

  useEffect(() => { checkHealth(); fetchContracts(); }, []);
  useEffect(() => { chatEnd.current?.scrollIntoView({ behavior: 'smooth' }); }, [qaHistory]);
  useEffect(() => { setQaHistory([]); setQaInput(''); setMobileDetailsOpen(false); }, [active?.id]);
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
  function handleFileSelect(f) {
    if (!f) return;
    setUploadErr(null);
    const ext = f.name.toLowerCase().slice(f.name.lastIndexOf('.'));
    if (!['.pdf', '.docx', '.doc'].includes(ext)) {
      setUploadErr('Only PDF, DOCX, and DOC files are accepted');
      return;
    }
    if (f.size > 10 * 1024 * 1024) {
      setUploadErr('File exceeds 10 MB limit');
      return;
    }
    setFile(f);
    if (!title.trim()) {
      const base = f.name.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
      setTitle(base.charAt(0).toUpperCase() + base.slice(1));
    }
  }
  async function handleUpload(e) {
    e.preventDefault();
    if (!file || !title.trim() || uploading) return;
    setUploading(true); setUploadErr(null); setUploadDone(null);
    try {
      const fd = new FormData();
      fd.append('title', title.trim());
      fd.append('file', file);
      const r = await fetch('/api/contracts', { method: 'POST', body: fd });
      const d = await r.json();
      if (!r.ok) throw new Error(d.message || 'Upload failed');
      setUploadDone(d);
      setToast({ type: 'success', msg: `Contract "${d.title}" created` });
      await fetchContracts();
    } catch (err) { setUploadErr(err.message); }
    finally { setUploading(false); }
  }
  async function handleAsk(questionText) {
    const q = (questionText || qaInput).trim();
    if (!q || !active || qaLoading) return;
    setQaInput('');
    setQaHistory(prev => [...prev, { role: 'user', text: q }]);
    setQaLoading(true);
    try {
      const r = await fetch(`/api/contracts/${active.id}/ask`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: q }),
      });
      const d = await r.json();
      if (!r.ok) throw new Error(d.message || 'Error getting answer');
      setQaHistory(prev => [...prev, {
        role: 'assistant',
        text: d.answer,
        excerpt: d.sourceExcerpt,
        found: d.answerFound,
      }]);
    } catch (err) {
      setQaHistory(prev => [...prev, {
        role: 'error',
        text: err.message || 'Failed to reach Q&A service',
      }]);
    } finally { setQaLoading(false); }
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
    <div className="flex flex-col md:flex-row h-screen bg-gray-50 overflow-hidden" style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      
      {/* MOBILE TOP BAR */}
      <div className="md:hidden bg-gray-900 text-white px-4 py-3 flex items-center justify-between shrink-0 shadow-md z-20">
        <div className="flex items-center gap-2.5">
          <button 
            onClick={() => setMobileMenuOpen(true)}
            className="p-1.5 -ml-1 text-gray-300 hover:text-white rounded-lg hover:bg-gray-800"
            aria-label="Open menu"
          >
            <Menu className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-indigo-600 flex items-center justify-center">
              <ShieldCheck className="w-4 h-4 text-white"/>
            </div>
            <span className="text-sm font-bold text-white tracking-tight">ContractVault</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className={`flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full bg-gray-800 ${health.ok?'text-green-400':'text-red-400'}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${health.ok?'bg-green-400 animate-pulse':'bg-red-500'}`}/>
            {health.ok ? 'Live' : 'Offline'}
          </div>
          <button
            onClick={() => setUploadOpen(true)}
            className="p-1.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
            title="Upload Contract"
          >
            <Plus className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* MOBILE SLIDE-OUT DRAWER */}
      {mobileMenuOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden" onClick={() => setMobileMenuOpen(false)}>
          <div className="fixed inset-0 bg-black/60 backdrop-blur-xs transition-opacity" />
          <div 
            className="relative w-64 max-w-[80vw] bg-gray-900 h-full flex flex-col z-10 shadow-2xl"
            onClick={e => e.stopPropagation()}
          >
            <div className="px-5 py-4 border-b border-gray-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-indigo-400" />
                <span className="font-bold text-white text-sm">ContractVault</span>
              </div>
              <button onClick={() => setMobileMenuOpen(false)} className="text-gray-400 hover:text-white p-1">
                <X className="w-5 h-5" />
              </button>
            </div>
            <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
              <p className="text-[10px] font-semibold text-gray-500 uppercase tracking-wider px-2 mb-1.5">Navigation</p>
              <button 
                onClick={() => { setActive(null); setMobileMenuOpen(false); }} 
                className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm ${!active?'bg-indigo-600 text-white':'text-gray-300 hover:bg-gray-800'}`}
              >
                <LayoutDashboard className="w-4 h-4" />
                <span>Dashboard</span>
                <span className="ml-auto text-xs bg-gray-800 text-gray-300 px-2 py-0.5 rounded-full">{contracts.length}</span>
              </button>
              <button 
                onClick={() => { setUploadOpen(true); setMobileMenuOpen(false); }} 
                className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-gray-300 hover:bg-gray-800"
              >
                <FileUp className="w-4 h-4" />
                <span>Upload Contract</span>
              </button>

              <p className="text-[10px] font-semibold text-gray-500 uppercase tracking-wider px-2 mt-5 mb-1.5">Filter by Status</p>
              {['ALL','DRAFT','REVIEW','APPROVED'].map(s => (
                <button 
                  key={s} 
                  onClick={() => { setStatusFilter(s); setMobileMenuOpen(false); }} 
                  className={`w-full flex items-center justify-between px-3 py-1.5 rounded-lg text-xs ${statusFilter===s?'bg-gray-800 text-white font-medium':'text-gray-400 hover:bg-gray-800'}`}
                >
                  <span>{s==='ALL'?'All Contracts':s.charAt(0)+s.slice(1).toLowerCase()}</span>
                  <span className="text-gray-500">{s==='ALL'?counts.total:counts[s]||0}</span>
                </button>
              ))}
            </nav>
            <div className="p-4 border-t border-gray-800 text-xs text-gray-400">
              ContractVault Production v1.0
            </div>
          </div>
        </div>
      )}

      {/* DESKTOP SIDEBAR */}
      <aside className="hidden md:flex w-56 bg-gray-900 flex-col shrink-0">
        <div className="px-5 py-5 border-b border-gray-800">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center">
              <ShieldCheck className="w-4 h-4 text-white"/>
            </div>
            <div>
              <p className="text-sm font-bold text-white">ContractVault</p>
              <p className="text-[10px] text-gray-500">Contract Workspace</p>
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

      {/* MAIN CONTAINER */}
      <div className="flex-1 flex flex-col overflow-hidden">
        
        {/* TOP BAR / DESKTOP HEADER */}
        <header className="bg-white border-b border-gray-200 px-4 md:px-6 py-3 md:py-3.5 flex items-center justify-between shrink-0">
          <div className="min-w-0 pr-2">
            <h1 className="text-sm md:text-base font-bold text-gray-900 truncate">
              {active ? 'Contract Q&A' : 'My Contracts'}
            </h1>
            <p className="text-[11px] md:text-xs text-gray-500 truncate">
              {active ? `${active.title} · #${active.id}` : `${visible.length} of ${contracts.length} records`}
            </p>
          </div>
          <div className="flex items-center gap-1.5 md:gap-2 shrink-0">
            {active && (
              <button 
                onClick={() => setActive(null)} 
                className="flex items-center gap-1 text-xs text-gray-600 hover:text-gray-900 px-2.5 md:px-3 py-1.5 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5"/> 
                <span className="hidden sm:inline">Back</span>
              </button>
            )}
            <button 
              onClick={() => { checkHealth(); fetchContracts(); }} 
              className="p-1.5 md:p-2 rounded-lg text-gray-500 hover:text-gray-900 hover:bg-gray-100 transition-colors"
              title="Refresh"
            >
              <RefreshCw className={`w-4 h-4 ${loading?'animate-spin':''}`}/>
            </button>
            <button 
              onClick={() => setUploadOpen(true)} 
              className="flex items-center gap-1.5 px-3 md:px-4 py-1.5 md:py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition-colors shadow-sm"
            >
              <Plus className="w-3.5 h-3.5"/> 
              <span className="hidden sm:inline">Upload Contract</span>
              <span className="sm:hidden">Upload</span>
            </button>
          </div>
        </header>

        {/* TOAST MESSAGE */}
        {toast && (
          <div className={`mx-4 md:mx-6 mt-3 shrink-0 flex items-center justify-between px-3 md:px-4 py-2 md:py-2.5 rounded-xl text-xs md:text-sm font-medium shadow-sm ${toast.type==='success'?'bg-green-50 text-green-800 border border-green-200':'bg-red-50 text-red-800 border border-red-200'}`}>
            <div className="flex items-center gap-2">
              {toast.type==='success'?<CheckCircle2 className="w-4 h-4 text-green-600 shrink-0"/>:<AlertCircle className="w-4 h-4 text-red-500 shrink-0"/>}
              <span className="truncate">{toast.msg}</span>
            </div>
            <button onClick={() => setToast(null)}><X className="w-4 h-4 opacity-60 hover:opacity-100"/></button>
          </div>
        )}

        {/* ======================================================== */}
        {/* DASHBOARD VIEW                                           */}
        {/* ======================================================== */}
        {!active && (
          <div className="flex-1 overflow-auto px-4 md:px-6 py-3 md:py-4 space-y-4">
            
            {/* STATS CARDS */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 md:gap-4">
              {[
                {label:'Total',value:counts.total,icon:FileText,color:'text-indigo-600 bg-indigo-50'},
                {label:'Draft',value:counts.DRAFT,icon:Clock,color:'text-amber-600 bg-amber-50'},
                {label:'In Review',value:counts.REVIEW,icon:Eye,color:'text-blue-600 bg-blue-50'},
                {label:'Approved',value:counts.APPROVED,icon:CheckCircle2,color:'text-green-600 bg-green-50'},
              ].map(s => (
                <div key={s.label} className="bg-white rounded-xl border border-gray-200 p-3 md:px-5 md:py-4 flex items-center gap-3 md:gap-4 shadow-xs">
                  <div className={`w-9 h-9 md:w-10 md:h-10 rounded-xl flex items-center justify-center shrink-0 ${s.color}`}>
                    <s.icon className="w-4 h-4 md:w-5 md:h-5"/>
                  </div>
                  <div>
                    <p className="text-xl md:text-2xl font-bold text-gray-900 leading-tight">{s.value}</p>
                    <p className="text-[11px] md:text-xs text-gray-500">{s.label}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* CONTRACT LIST CONTAINER */}
            <div className="bg-white rounded-xl border border-gray-200 shadow-xs overflow-hidden">
              
              {/* SEARCH & FILTERS BAR */}
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2.5 p-3 md:px-4 md:py-3 border-b border-gray-100 bg-white">
                <div className="relative flex-1 max-w-sm">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400"/>
                  <input 
                    value={search} 
                    onChange={e => setSearch(e.target.value)} 
                    placeholder="Search contracts…"
                    className="w-full pl-8 pr-3 py-1.5 text-xs md:text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                
                {/* FILTER BUTTONS */}
                <div className="flex items-center gap-1 overflow-x-auto pb-1 sm:pb-0">
                  {['ALL','DRAFT','REVIEW','APPROVED'].map(s => (
                    <button 
                      key={s} 
                      onClick={() => setStatusFilter(s)}
                      className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${statusFilter===s?'bg-indigo-600 text-white':'text-gray-600 hover:bg-gray-100'}`}
                    >
                      {s==='ALL'?'All':s.charAt(0)+s.slice(1).toLowerCase()}
                    </button>
                  ))}
                </div>
              </div>

              {/* EMPTY & LOADING STATES */}
              {loading && visible.length===0 ? (
                <div className="text-center py-16 text-gray-400">
                  <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2 text-indigo-500"/>
                  <p className="text-xs">Loading contracts…</p>
                </div>
              ) : visible.length===0 ? (
                <div className="text-center py-16 px-4">
                  <FileText className="w-10 h-10 mx-auto mb-2 text-gray-300"/>
                  <p className="text-sm font-medium text-gray-500">{search||statusFilter!=='ALL'?'No contracts match your filter':'No contracts yet'}</p>
                  {!search&&statusFilter==='ALL'&& (
                    <button onClick={() => setUploadOpen(true)} className="mt-3 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700">
                      Upload First Contract
                    </button>
                  )}
                </div>
              ) : (
                <>
                  {/* MOBILE CARDS VIEW (md:hidden) */}
                  <div className="block md:hidden divide-y divide-gray-100">
                    {visible.map(c => (
                      <div key={c.id} className="p-3.5 space-y-2.5">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0 flex-1">
                            <span className="text-[10px] font-mono text-gray-400 mr-1.5">#{c.id}</span>
                            <span className="font-semibold text-gray-900 text-sm">{c.title}</span>
                          </div>
                          <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full shrink-0 ${STATUS_STYLE[c.status]?.badge}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${STATUS_STYLE[c.status]?.dot}`}/>
                            {c.status}
                          </span>
                        </div>

                        <div className="flex items-center justify-between text-xs text-gray-500">
                          <div className="truncate max-w-[200px]">
                            {c.originalFilename ? (
                              <a 
                                href={`/api/contracts/${c.id}/document`} 
                                target="_blank" 
                                rel="noreferrer"
                                className="text-indigo-600 hover:underline flex items-center gap-1 truncate"
                              >
                                <FileCode className="w-3.5 h-3.5 shrink-0" />
                                <span className="truncate">{c.originalFilename}</span>
                              </a>
                            ) : <span>No document</span>}
                          </div>
                          <span className="text-[11px] text-gray-400 shrink-0">{fmt(c.createdAt)}</span>
                        </div>

                        {/* MOBILE ACTION BUTTONS */}
                        <div className="flex items-center gap-2 pt-1">
                          <button
                            onClick={() => setActive(c)}
                            className="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-50 text-indigo-700 hover:bg-indigo-100 text-xs font-semibold"
                          >
                            <MessageSquare className="w-3.5 h-3.5" />
                            <span>Ask Q&A</span>
                          </button>

                          {PERMITTED[c.status]?.map(t => (
                            <button
                              key={t}
                              onClick={() => handleStatusChange(c.id, t)}
                              disabled={updatingId === c.id}
                              className="px-2.5 py-1.5 rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-50 text-xs font-medium"
                            >
                              {updatingId === c.id ? <Loader2 className="w-3 h-3 animate-spin"/> : (TRANSITION_LABEL[`${c.status}_${t}`] || t)}
                            </button>
                          ))}

                          <button
                            onClick={() => setDeleteTarget(c)}
                            className="p-1.5 text-gray-400 hover:text-red-600 rounded-lg hover:bg-red-50 border border-gray-200"
                            title="Delete"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* DESKTOP TABLE VIEW (hidden md:block) */}
                  <div className="hidden md:block overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="bg-gray-50 border-b border-gray-100">
                          {['ID','Title','File','Uploaded','Status','Actions'].map(h => (
                            <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-50">
                        {visible.map(c => (
                          <tr key={c.id} className="hover:bg-gray-50/80 transition-colors">
                            <td className="px-4 py-3 font-mono text-xs text-gray-400">#{c.id}</td>
                            <td className="px-4 py-3 font-medium text-gray-900 max-w-xs truncate">{c.title}</td>
                            <td className="px-4 py-3 text-xs text-gray-500 max-w-[180px] truncate">
                              {c.originalFilename ? (
                                <a 
                                  href={`/api/contracts/${c.id}/document`} 
                                  target={c.originalFilename.endsWith('.pdf')?'_blank':undefined} 
                                  download={!c.originalFilename.endsWith('.pdf')?c.originalFilename:undefined}
                                  className="text-indigo-600 hover:underline flex items-center gap-1 truncate"
                                >
                                  {c.originalFilename}
                                  {c.originalFilename.endsWith('.pdf')?<ExternalLink className="w-2.5 h-2.5 shrink-0"/>:<Download className="w-2.5 h-2.5 shrink-0"/>}
                                </a>
                              ) : <span className="text-gray-300">None</span>}
                            </td>
                            <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{fmt(c.createdAt)}</td>
                            <td className="px-4 py-3 whitespace-nowrap">
                              <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full ${STATUS_STYLE[c.status]?.badge}`}>
                                <span className={`w-1.5 h-1.5 rounded-full ${STATUS_STYLE[c.status]?.dot}`}/>
                                {c.status}
                              </span>
                            </td>
                            <td className="px-4 py-3 whitespace-nowrap">
                              <div className="flex items-center gap-1.5">
                                <button 
                                  onClick={() => setActive(c)} 
                                  className="px-2.5 py-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800 hover:bg-indigo-50 rounded-lg flex items-center gap-1 transition-colors"
                                >
                                  <MessageSquare className="w-3.5 h-3.5"/> Q&amp;A
                                </button>
                                {PERMITTED[c.status]?.map(t => (
                                  <button 
                                    key={t} 
                                    onClick={() => handleStatusChange(c.id, t)} 
                                    disabled={updatingId===c.id}
                                    className="px-2 py-1 text-xs border border-gray-200 text-gray-700 rounded-lg hover:bg-gray-100 disabled:opacity-40 transition-colors"
                                  >
                                    {updatingId===c.id?<Loader2 className="w-3 h-3 animate-spin"/>:(TRANSITION_LABEL[`${c.status}_${t}`]||t)}
                                  </button>
                                ))}
                                <button 
                                  onClick={() => setDeleteTarget(c)} 
                                  className="p-1 text-gray-400 hover:text-red-600 rounded-md hover:bg-red-50 transition-colors ml-1" 
                                  title="Delete Contract"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* CONTRACT Q&A VIEW                                        */}
        {/* ======================================================== */}
        {active && (
          <div className="flex-1 flex flex-col md:flex-row overflow-hidden bg-gray-50">
            
            {/* MOBILE TOP CONTRACT INFO BAR (md:hidden) */}
            <div className="md:hidden bg-white border-b border-gray-200 px-4 py-2.5 flex items-center justify-between shrink-0 shadow-xs">
              <div className="min-w-0 flex-1 mr-2">
                <div className="flex items-center gap-1.5">
                  <span className={`inline-flex items-center text-[10px] font-semibold px-1.5 py-0.2 rounded ${STATUS_STYLE[active.status]?.badge}`}>
                    {active.status}
                  </span>
                  <p className="text-xs font-bold text-gray-900 truncate">{active.title}</p>
                </div>
              </div>
              <button 
                onClick={() => setMobileDetailsOpen(!mobileDetailsOpen)} 
                className="text-xs text-indigo-600 font-medium flex items-center gap-1 bg-indigo-50 px-2 py-1 rounded-lg"
              >
                <Info className="w-3.5 h-3.5" />
                <span>{mobileDetailsOpen ? 'Hide' : 'Details'}</span>
              </button>
            </div>

            {/* MOBILE CONTRACT DETAILS DROPDOWN */}
            {mobileDetailsOpen && (
              <div className="md:hidden bg-white border-b border-gray-200 p-4 space-y-3 shrink-0 shadow-sm text-xs">
                <div className="grid grid-cols-2 gap-2 pb-2 border-b border-gray-100">
                  <div><span className="text-gray-400">ID:</span> <span className="font-mono font-bold">#{active.id}</span></div>
                  <div><span className="text-gray-400">Created:</span> <span>{fmt(active.createdAt)}</span></div>
                  <div className="col-span-2">
                    <span className="text-gray-400">File: </span>
                    {active.originalFilename ? (
                      <a href={`/api/contracts/${active.id}/document`} target="_blank" rel="noreferrer" className="text-indigo-600 font-medium underline inline-flex items-center gap-1">
                        {active.originalFilename} <ExternalLink className="w-2.5 h-2.5"/>
                      </a>
                    ) : 'None'}
                  </div>
                </div>

                {/* STATUS ACTIONS ON MOBILE */}
                <div>
                  <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Change Status</p>
                  <div className="flex gap-2">
                    {PERMITTED[active.status]?.map(t => (
                      <button 
                        key={t} 
                        onClick={() => handleStatusChange(active.id, t)} 
                        disabled={!!updatingId}
                        className="flex-1 py-1.5 px-2 rounded-lg border border-gray-200 text-gray-700 bg-gray-50 text-xs font-medium"
                      >
                        {TRANSITION_LABEL[`${active.status}_${t}`] || t}
                      </button>
                    ))}
                    <button 
                      onClick={() => setDeleteTarget(active)}
                      className="px-3 py-1.5 rounded-lg border border-red-200 text-red-600 bg-red-50 text-xs font-medium flex items-center gap-1"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* DESKTOP CONTRACT DETAILS SIDE PANEL (hidden md:flex) */}
            <div className="hidden md:flex w-60 border-r border-gray-200 bg-white flex-col shrink-0">
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

            {/* CHAT INTERFACE (FULL WIDTH ON MOBILE) */}
            <div className="flex-1 flex flex-col bg-gray-50 overflow-hidden">
              
              {noText && (
                <div className="mx-4 md:mx-5 mt-3 md:mt-4 flex items-start gap-2.5 bg-amber-50 border border-amber-200 rounded-xl px-3 md:px-4 py-2.5 md:py-3 shrink-0">
                  <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5"/>
                  <div className="text-xs text-amber-800">
                    <p className="font-semibold mb-0.5">No text extracted</p>
                    This appears to be a scanned image. Q&A requires text-based PDF or DOCX.
                  </div>
                </div>
              )}

              {/* CHAT MESSAGES SCROLL AREA */}
              <div className="flex-1 overflow-y-auto px-3.5 md:px-5 py-4 space-y-3 md:space-y-4">
                {qaHistory.length===0 && (
                  <div className="flex flex-col items-center text-center py-6 md:py-10">
                    <div className="w-12 h-12 md:w-14 md:h-14 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center mb-3 md:mb-4">
                      <Sparkles className="w-6 h-6 md:w-7 md:h-7 text-indigo-500"/>
                    </div>
                    <h2 className="text-sm md:text-base font-bold text-gray-900 mb-1">Ask anything about this contract</h2>
                    <p className="text-xs text-gray-500 max-w-xs mb-4 md:mb-6">Questions are matched against the extracted document clauses.</p>
                    
                    {!noText && (
                      <div className="flex flex-wrap gap-1.5 md:gap-2 justify-center max-w-lg px-2">
                        {SUGGESTED.map(q => (
                          <button 
                            key={q} 
                            onClick={() => handleAsk(q)}
                            className="text-xs px-2.5 md:px-3 py-1.5 rounded-full bg-white border border-gray-200 text-gray-600 hover:border-indigo-400 hover:text-indigo-600 hover:bg-indigo-50 transition-all shadow-2xs"
                          >
                            {q}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {qaHistory.map((msg, i) => (
                  <div key={i} className={`flex gap-2.5 md:gap-3 ${msg.role==='user'?'justify-end':'justify-start'}`}>
                    {msg.role!=='user' && (
                      <div className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${msg.role==='error'?'bg-red-500':'bg-indigo-600'}`}>
                        <Bot className="w-4 h-4 text-white"/>
                      </div>
                    )}
                    <div className="max-w-[85%] md:max-w-[78%]">
                      {msg.role==='user' && (
                        <div className="bg-indigo-600 text-white px-3.5 md:px-4 py-2 md:py-2.5 rounded-2xl rounded-tr-xs text-xs md:text-sm shadow-xs">
                          {msg.text}
                        </div>
                      )}
                      {msg.role==='assistant' && (
                        <div className={`px-3.5 md:px-4 py-2.5 md:py-3 rounded-2xl rounded-tl-xs text-xs md:text-sm border shadow-xs ${msg.found?'bg-white border-gray-200 text-gray-800':'bg-amber-50 border-amber-200 text-amber-900'}`}>
                          {!msg.found&&<AlertTriangle className="w-3.5 h-3.5 text-amber-500 inline mr-1.5 mb-0.5"/>}
                          <span>{msg.text}</span>
                          {msg.found&&msg.excerpt && (
                            <div className="mt-2.5 pt-2.5 border-t border-gray-100">
                              <p className="text-[10px] uppercase tracking-wider text-indigo-500 font-semibold mb-1">Source excerpt</p>
                              <blockquote className="text-xs italic text-gray-600 bg-gray-50 rounded-lg px-2.5 py-1.5 border-l-2 border-indigo-400">"{msg.excerpt}"</blockquote>
                            </div>
                          )}
                        </div>
                      )}
                      {msg.role==='error' && (
                        <div className="bg-red-50 border border-red-200 text-red-700 px-3.5 py-2.5 rounded-2xl rounded-tl-xs text-xs md:text-sm flex items-center gap-2">
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
                  <div className="flex gap-2.5 md:gap-3">
                    <div className="w-7 h-7 rounded-full bg-indigo-600 flex items-center justify-center shrink-0"><Bot className="w-4 h-4 text-white"/></div>
                    <div className="bg-white border border-gray-200 px-3.5 py-2.5 rounded-2xl rounded-tl-xs flex items-center gap-1.5 shadow-xs">
                      {[0,150,300].map(d=><span key={d} className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce" style={{animationDelay:`${d}ms`}}/>)}
                    </div>
                  </div>
                )}
                <div ref={chatEnd}/>
              </div>

              {/* INPUT BAR AT BOTTOM */}
              <div className="shrink-0 border-t border-gray-200 bg-white p-3 md:px-5 md:py-4">
                {noText ? (
                  <div className="flex items-center gap-2 text-xs text-amber-600 justify-center py-1">
                    <AlertTriangle className="w-3.5 h-3.5"/>Q&A unavailable — no text could be extracted
                  </div>
                ) : (
                  <form onSubmit={e=>{e.preventDefault();handleAsk();}} className="flex gap-2 max-w-4xl mx-auto">
                    <input 
                      value={qaInput} 
                      onChange={e=>setQaInput(e.target.value)}
                      placeholder={`Ask a question…`}
                      disabled={qaLoading} 
                      maxLength={500}
                      className="flex-1 border border-gray-200 rounded-xl px-3.5 py-2 md:py-2.5 text-xs md:text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50 bg-gray-50"
                    />
                    <button 
                      type="submit" 
                      disabled={qaLoading||!qaInput.trim()}
                      className="px-3.5 md:px-4 py-2 md:py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs md:text-sm disabled:opacity-40 transition-colors flex items-center gap-1.5 shadow-xs shrink-0"
                    >
                      {qaLoading?<Loader2 className="w-4 h-4 animate-spin"/>:<Send className="w-4 h-4"/>}
                    </button>
                  </form>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ======================================================== */}
      {/* UPLOAD MODAL                                             */}
      {/* ======================================================== */}
      {uploadOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4" onClick={e=>{if(e.target===e.currentTarget)closeModal();}}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-5 md:p-6 space-y-4 md:space-y-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-indigo-50 border border-indigo-100 flex items-center justify-center"><FileUp className="w-4 h-4 text-indigo-600"/></div>
                <h2 className="text-sm md:text-base font-bold text-gray-900">Upload Contract</h2>
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
                      <p className="text-[11px] text-amber-800">No text was extracted — likely a scanned/image PDF. File saved, but Q&A won't be available.</p>
                    </div>
                  )}
                </div>
                <div className="flex gap-2.5 justify-end">
                  <button onClick={() => setUploadDone(null)} className="px-3.5 py-2 rounded-xl text-xs font-medium border border-gray-200 text-gray-700 hover:bg-gray-50">Upload Another</button>
                  <button onClick={closeModal} className="px-4 py-2 rounded-xl text-xs font-semibold bg-indigo-600 text-white hover:bg-indigo-700">Done</button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleUpload} className="space-y-3.5 md:space-y-4">
                {uploadErr && (
                  <div className="flex items-start gap-2 bg-red-50 border border-red-200 rounded-xl px-3 py-2.5 text-xs text-red-700">
                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-red-500"/>{uploadErr}
                  </div>
                )}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">Contract Title <span className="text-red-500">*</span></label>
                  <input value={title} onChange={e=>setTitle(e.target.value)} placeholder="e.g., Employment Agreement"
                    disabled={uploading} className="w-full border border-gray-200 rounded-xl px-3.5 py-2 text-xs md:text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"/>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">
                    Document File <span className="text-red-500">*</span>
                    <span className="ml-1 text-[10px] text-gray-400 font-normal">PDF, DOCX · max 10 MB</span>
                  </label>
                  <label className={`block cursor-pointer border-2 border-dashed rounded-xl p-4 md:p-5 text-center transition-all ${file?'border-indigo-400 bg-indigo-50':'border-gray-200 hover:border-indigo-300 hover:bg-gray-50'}`}>
                    <input type="file" accept=".pdf,.docx,.doc" className="sr-only" disabled={uploading} onChange={e=>handleFileSelect(e.target.files?.[0])}/>
                    {file ? (
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <FileCode className="w-5 h-5 text-indigo-500"/>
                          <div className="text-left"><p className="text-xs md:text-sm font-medium text-gray-900 truncate max-w-[200px]">{file.name}</p><p className="text-[10px] text-gray-500">{(file.size/1024).toFixed(1)} KB</p></div>
                        </div>
                        <button type="button" onClick={e=>{e.preventDefault();setFile(null);}} className="p-1 text-gray-400 hover:text-red-500"><X className="w-4 h-4"/></button>
                      </div>
                    ) : (
                      <>
                        <FileUp className="w-7 h-7 text-gray-300 mx-auto mb-1.5"/>
                        <p className="text-xs md:text-sm text-gray-500"><span className="text-indigo-600 font-medium">Click to choose</span> or drag file</p>
                        <p className="text-[10px] text-gray-400 mt-0.5">Text-based PDF recommended for Q&A</p>
                      </>
                    )}
                  </label>
                </div>
                <div className="flex justify-end gap-2.5 pt-1">
                  <button type="button" onClick={closeModal} disabled={uploading} className="px-3.5 py-2 rounded-xl text-xs font-medium text-gray-600 hover:bg-gray-100">Cancel</button>
                  <button type="submit" disabled={uploading||!file||!title.trim()}
                    className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold disabled:opacity-40 transition-colors shadow-xs">
                    {uploading?<><Loader2 className="w-3.5 h-3.5 animate-spin"/><span>Uploading…</span></>:<><Upload className="w-3.5 h-3.5"/><span>Upload Contract</span></>}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* DELETE CONFIRMATION MODAL                                */}
      {/* ======================================================== */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-sm w-full p-5 md:p-6 shadow-xl border border-gray-100">
            <div className="w-10 h-10 rounded-full bg-red-100 text-red-600 flex items-center justify-center mb-3">
              <Trash2 className="w-5 h-5" />
            </div>
            <h3 className="text-sm md:text-base font-bold text-gray-900 mb-1">Delete Contract?</h3>
            <p className="text-xs text-gray-500 mb-4 leading-relaxed">
              Are you sure you want to delete <span className="font-semibold text-gray-800">"{deleteTarget.title}"</span>? This will remove the record and any document file from storage.
            </p>
            <div className="flex items-center justify-end gap-2">
              <button
                onClick={() => setDeleteTarget(null)}
                disabled={deleting}
                className="px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => handleDeleteContract(deleteTarget.id)}
                disabled={deleting}
                className="px-3.5 py-1.5 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 rounded-lg transition-colors flex items-center gap-1.5 shadow-xs"
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
