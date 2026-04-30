import React, { useState, useEffect } from 'react';
import { Database, ShieldCheck, PlayCircle, LogIn, Search, TerminalSquare, UploadCloud, Network, CheckCircle2 } from 'lucide-react';
import Editor from '@monaco-editor/react';
import { ReactFlow, Background, Controls, useNodesState, useEdgesState } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import * as api from './api';
import OnboardStep from './OnboardStep';

/* ── Helpers ───────────────────────────────────────────────── */
const Nav = ({ active, onClick, icon, label }: any) => (
  <button className={`nav-btn ${active ? 'active' : ''}`} onClick={onClick}>{icon}<span>{label}</span></button>
);

/* ── App Shell ─────────────────────────────────────────────── */
export default function App() {
  const [auth, setAuth] = useState(!!localStorage.getItem('semlink_token'));
  const [tab, setTab] = useState('dashboard');
  const [health, setHealth] = useState<any>(null);
  useEffect(() => { if (auth) api.getHealth().then(setHealth).catch(() => {}); }, [auth, tab]);
  if (!auth) return <LoginPage onOk={() => setAuth(true)} />;
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand-block"><div className="mark">S</div><div><div className="brand-name">SEMLINK</div><div className="brand-sub">Platform v3</div></div></div>
        <div className="nav-group"><div className="nav-label">Core</div>
          <Nav active={tab==='dashboard'} onClick={()=>setTab('dashboard')} icon={<PlayCircle size={18}/>} label="Dashboard"/>
          <Nav active={tab==='connections'} onClick={()=>setTab('connections')} icon={<Database size={18}/>} label="Connections"/>
          <Nav active={tab==='explore'} onClick={()=>setTab('explore')} icon={<Search size={18}/>} label="Explore"/>
        </div>
        <div className="nav-group"><div className="nav-label">Operations</div>
          <Nav active={tab==='query'} onClick={()=>setTab('query')} icon={<TerminalSquare size={18}/>} label="Query Studio"/>
          <Nav active={tab==='validate'} onClick={()=>setTab('validate')} icon={<ShieldCheck size={18}/>} label="Validation"/>
          <Nav active={tab==='lineage'} onClick={()=>setTab('lineage')} icon={<Network size={18}/>} label="Lineage"/>
        </div>
        <div className="nav-group"><div className="nav-label">Onboarding</div>
          <Nav active={tab==='onboard'} onClick={()=>setTab('onboard')} icon={<UploadCloud size={18}/>} label="R2O Workflow"/>
          <Nav active={false} onClick={()=>{api.setAuthToken('');setAuth(false)}} icon={<LogIn size={18}/>} label="Logout"/>
        </div>
        <div style={{flexGrow:1}}/>
        <div style={{padding:12,background:'var(--panel)',borderRadius:8,border:'1px solid var(--line)',fontSize:12}}>
          <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:6}}>
            <div style={{width:8,height:8,borderRadius:'50%',background:health?.status==='UP'?'var(--good)':'var(--bad)'}}/>
            <span className="text-secondary">API: {health?.status||'…'}</span>
          </div>
          <div className="text-secondary">Pipeline: {health?.pipelineRan?'✓ Ready':'Idle'}</div>
        </div>
      </aside>
      <main className="content">
        <header className="topbar"><h1 className="page-title">{tab.charAt(0).toUpperCase()+tab.slice(1)}</h1>{health?.pipelineRan&&<div className="badge good">Pipeline Live</div>}</header>
        <div className="workspace">
          {tab==='dashboard'&&<Dashboard health={health}/>}
          {tab==='connections'&&<Connections/>}
          {tab==='explore'&&<Explore/>}
          {tab==='query'&&<QueryStudio/>}
          {tab==='validate'&&<Validate/>}
          {tab==='lineage'&&<Lineage health={health}/>}
          {tab==='onboard'&&<OnboardStep onComplete={() => api.getHealth().then(setHealth)} />}
        </div>
      </main>
    </div>
  );
}

/* ── Login ──────────────────────────────────────────────────── */
function LoginPage({onOk}:{onOk:()=>void}) {
  const [u,setU]=useState('admin'),[p,setP]=useState('admin123'),[err,setErr]=useState('');
  const go=async(e:React.FormEvent)=>{e.preventDefault();try{await api.login(u,p);onOk()}catch{setErr('Invalid credentials')}};
  return (
    <div style={{minHeight:'100vh',display:'grid',placeItems:'center',background:'var(--bg)'}}>
      <form className="glass-card" onSubmit={go} style={{width:380,padding:36,display:'flex',flexDirection:'column',gap:16}}>
        <div style={{textAlign:'center',marginBottom:12}}><div className="mark" style={{margin:'0 auto 16px'}}>S</div><h2 style={{fontSize:24}}>SEMLINK Platform</h2><p className="text-secondary">Semantic Data Integration Console</p></div>
        {err&&<div className="banner bad">{err}</div>}
        <input className="input" placeholder="Username" value={u} onChange={e=>setU(e.target.value)}/>
        <input className="input" type="password" placeholder="Password" value={p} onChange={e=>setP(e.target.value)}/>
        <button className="btn-primary" type="submit" style={{marginTop:8}}>Sign In</button>
        <p style={{fontSize:11,color:'var(--muted)',textAlign:'center'}}>Demo credentials: admin / admin123</p>
      </form>
    </div>
  );
}

/* ── Dashboard ─────────────────────────────────────────────── */
function Dashboard({health}:{health:any}) {
  const [running,setRunning]=useState(false);
  const trigger=async()=>{setRunning(true);try{await api.runPipeline()}finally{setRunning(false);window.location.reload()}};
  const cards=[
    {label:'Universities',value:health?.universitiesInferred||0},
    {label:'Students',value:health?.studentsInferred||0},
    {label:'Triples',value:health?.totalTriples||0},
    {label:'Connections',value:health?.connectionsCount||0},
  ];
  return (
    <div style={{display:'flex',flexDirection:'column',gap:24}}>
      <div className="glass-card" style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}>
        <div><h2 style={{marginBottom:6}}>System Overview</h2><p className="text-secondary">AICTE semantic knowledge graph status. Run the pipeline to load ontologies, infer triples, and enable querying.</p></div>
        <button className="btn-primary" onClick={trigger} disabled={running}>{running?'Running…':'Run Full Pipeline'}</button>
      </div>
      <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:16}}>
        {cards.map(c=><div key={c.label} className="glass-card stat-card"><div className="stat-label">{c.label}</div><div className="stat-value">{c.value}</div></div>)}
      </div>
      {!health?.pipelineRan&&<div className="banner info">💡 Click "Run Full Pipeline" to load all university ontologies, apply alignment rules, and build the inferred knowledge graph.</div>}
      {health?.pipelineRan&&(
        <div className="glass-card">
          <h3 style={{marginBottom:12}}>Demo Workflow</h3>
          <div className="step-track">
            <div className="step-item done"><div className="step-num">1</div><div className="step-name">Central Ontology</div></div>
            <div className="step-item done"><div className="step-num">2</div><div className="step-name">Add Universities</div></div>
            <div className="step-item done"><div className="step-num">3</div><div className="step-name">R2RML Mapping</div></div>
            <div className="step-item done"><div className="step-num">4</div><div className="step-name">Align & Infer</div></div>
            <div className="step-item done"><div className="step-num">5</div><div className="step-name">Query & Validate</div></div>
          </div>
          <p className="text-secondary">All pipeline stages complete. Navigate to Query Studio to run SPARQL or natural language queries.</p>
        </div>
      )}
    </div>
  );
}

/* ── Connections ────────────────────────────────────────────── */
function Connections() {
  const [list,setList]=useState<any[]>([]),[show,setShow]=useState(false);
  const reload=()=>api.getConnections().then(setList).catch(()=>{});
  useEffect(()=>{reload()},[]);
  const add=async(e:React.FormEvent)=>{e.preventDefault();const f=new FormData(e.target as HTMLFormElement);await api.addConnection({id:f.get('id'),type:f.get('type'),database:f.get('database')});setShow(false);reload()};
  const icons:Record<string,string>={mysql:'🐬',postgres:'🐘',mongodb:'🍃',neo4j:'🔵',owl:'📄',csv:'📊',redis:'🔴'};
  return (
    <div style={{display:'flex',flexDirection:'column',gap:20}}>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}><h2>Data Sources</h2><button className="btn-primary" onClick={()=>setShow(!show)}>+ Add Connection</button></div>
      {show&&<form className="glass-card" onSubmit={add} style={{display:'grid',gridTemplateColumns:'1fr 1fr 2fr auto',gap:12,alignItems:'end'}}>
        <div><div className="nav-label">ID</div><input className="input" name="id" placeholder="source-id" required/></div>
        <div><div className="nav-label">Type</div><select className="input" name="type"><option value="mysql">MySQL</option><option value="postgres">PostgreSQL</option><option value="mongodb">MongoDB</option><option value="neo4j">Neo4j</option><option value="owl">OWL File</option></select></div>
        <div><div className="nav-label">Database / Path</div><input className="input" name="database" placeholder="jdbc:mysql://... or file path" required/></div>
        <button className="btn-primary" type="submit">Save</button>
      </form>}
      <div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(280px,1fr))',gap:16}}>
        {list.map((c:any)=><div key={c.id} className="glass-card" style={{position:'relative'}}>
          <button className="btn-secondary" style={{position:'absolute',top:12,right:12,padding:'4px 8px',fontSize:11}} onClick={async()=>{await api.removeConnection(c.id);reload()}}>✕</button>
          <div style={{fontSize:28,marginBottom:8}}>{icons[c.type]||'📁'}</div>
          <h3>{c.id}</h3><div className="badge blue" style={{margin:'6px 0'}}>{c.type}</div>
          <p className="text-secondary" style={{fontSize:12,wordBreak:'break-all'}}>{c.database||c.host||'Local'}</p>
        </div>)}
        {list.length===0&&<p className="text-secondary">No connections yet. The pipeline uses built-in ontology sources.</p>}
      </div>
    </div>
  );
}

/* ── Explore ───────────────────────────────────────────────── */
function Explore() {
  const [data,setData]=useState<any[]>([]);
  useEffect(()=>{api.getMappings().then(setData).catch(()=>{});},[]);
  return (
    <div style={{display:'flex',flexDirection:'column',gap:20}}>
      <div><h2>Schema Alignments</h2><p className="text-secondary" style={{marginTop:4}}>How local university terms map to the central AICTE ontology.</p></div>
      {data.length===0?<div className="banner info">Run the pipeline first to generate mapping suggestions.</div>:
      <div className="glass-card" style={{padding:0,overflow:'hidden'}}><table className="data-table"><thead><tr><th>Source</th><th>Kind</th><th>Local Term</th><th>AICTE Term</th><th>Score</th><th>Method</th></tr></thead>
        <tbody>{data.map((m,i)=><tr key={i}><td><span className="badge blue">{m.ontology}</span></td><td>{m.kind}</td><td style={{fontFamily:'var(--mono)'}}>{m.localTerm}</td><td style={{fontFamily:'var(--mono)',color:'var(--accent)'}}>{m.suggestedAicteTerm}</td>
          <td><div style={{display:'flex',alignItems:'center',gap:8}}><div style={{flex:1,height:4,background:'var(--line)',borderRadius:2}}><div style={{width:`${parseFloat(m.score)*100}%`,height:'100%',background:'var(--accent)',borderRadius:2}}/></div><span style={{fontSize:12}}>{m.score}</span></div></td>
          <td className="text-secondary">{m.method}</td></tr>)}</tbody></table></div>}
    </div>
  );
}

/* ── Query Studio ──────────────────────────────────────────── */
function QueryStudio() {
  const [nl,setNl]=useState('');
  const [sparql,setSparql]=useState('PREFIX aicte: <https://semlink.example.org/aicte#>\nSELECT ?student ?name ?cgpa\nWHERE {\n  ?student a aicte:Student ;\n           aicte:name ?name ;\n           aicte:cgpa ?cgpa .\n}\nORDER BY DESC(?cgpa)\nLIMIT 20');
  const [result,setResult]=useState<any>(null);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');
  const [genSparql,setGenSparql]=useState('');
  const examples=['Show all students with CGPA above 9','List colleges by university','Find students enrolled in more than 5 courses','Count students by department'];

  const runNl=async(q?:string)=>{const question=q||nl;if(!question)return;setNl(question);setLoading(true);setError('');setGenSparql('');
    try{const r=await api.naturalLanguageQuery(question);setSparql(r.generatedSparql);setGenSparql(r.generatedSparql);setResult(r)}catch(e:any){setError(e.message)}finally{setLoading(false)}};
  const runSp=async()=>{setLoading(true);setError('');setGenSparql('');try{setResult(await api.executeSparql(sparql))}catch(e:any){setError(e.message)}finally{setLoading(false)}};

  return (
    <div style={{display:'flex',flexDirection:'column',gap:16,height:'calc(100vh - 120px)'}}>
      <div className="glass-card" style={{padding:16}}>
        <div className="nav-label" style={{marginBottom:8}}>🤖 Natural Language Query (AI-powered)</div>
        <div style={{display:'flex',gap:10}}>
          <input className="input" style={{flex:1}} placeholder="Ask in English, e.g. Show me students with CGPA above 9…" value={nl} onChange={e=>setNl(e.target.value)} onKeyDown={e=>e.key==='Enter'&&runNl()}/>
          <button className="btn-primary" onClick={()=>runNl()} disabled={loading}>{loading?'Generating…':'Ask AI'}</button>
        </div>
        <div style={{display:'flex',gap:8,marginTop:10,flexWrap:'wrap'}}>
          {examples.map(ex=><button key={ex} className="btn-secondary" style={{fontSize:11,padding:'4px 10px'}} onClick={()=>runNl(ex)}>{ex}</button>)}
        </div>
        {genSparql&&<div className="banner info" style={{marginTop:10}}>✨ AI generated SPARQL from your question. See editor below.</div>}
      </div>
      <div style={{display:'flex',gap:16,flex:1,minHeight:0}}>
        <div className="glass-card" style={{flex:1,display:'flex',flexDirection:'column',padding:0,overflow:'hidden'}}>
          <div style={{padding:'10px 16px',borderBottom:'1px solid var(--line)',display:'flex',justifyContent:'space-between',alignItems:'center'}}>
            <span className="nav-label" style={{margin:0}}>SPARQL Editor</span>
            <button className="btn-secondary" onClick={runSp} disabled={loading} style={{padding:'4px 12px',fontSize:12}}><PlayCircle size={14} style={{marginRight:4,verticalAlign:'middle'}}/> Run</button>
          </div>
          <div style={{flex:1}}><Editor height="100%" defaultLanguage="sparql" theme="vs-dark" value={sparql} onChange={v=>setSparql(v||'')} options={{minimap:{enabled:false},fontSize:13,fontFamily:'JetBrains Mono',scrollBeyondLastLine:false}}/></div>
        </div>
        <div className="glass-card" style={{flex:1,display:'flex',flexDirection:'column',overflow:'hidden'}}>
          <h3 style={{marginBottom:12}}>Results {result?.rows&&<span className="badge purple" style={{marginLeft:8}}>{result.rows.length} rows</span>}</h3>
          {error&&<div className="banner bad" style={{marginBottom:12}}>{error}</div>}
          <div style={{flex:1,overflow:'auto'}}>
            {result?.columns?<table className="data-table"><thead><tr>{result.columns.map((c:string)=><th key={c}>{c}</th>)}</tr></thead>
              <tbody>{result.rows.length?result.rows.map((r:any,i:number)=><tr key={i}>{result.columns.map((c:string)=><td key={c} style={{fontFamily:'var(--mono)',fontSize:12}}>{r[c]}</td>)}</tr>)
              :<tr><td colSpan={result.columns.length} style={{textAlign:'center',color:'var(--muted)'}}>No results</td></tr>}</tbody></table>
            :<div style={{color:'var(--muted)',textAlign:'center',marginTop:60}}>Run a query to see results here.</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── Validate ──────────────────────────────────────────────── */
function Validate() {
  const [report,setReport]=useState<any>(null),[loading,setLoading]=useState(true);
  useEffect(()=>{api.runValidation().then(setReport).catch(()=>{}).finally(()=>setLoading(false))},[]);
  return (
    <div style={{display:'flex',flexDirection:'column',gap:20}}>
      <div className="glass-card" style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}>
        <div><h2>SHACL Validation</h2><p className="text-secondary" style={{marginTop:4}}>Checks the inferred graph against AICTE data shapes.</p></div>
        {report&&<div className={`badge ${report.conforms?'good':'bad'}`} style={{fontSize:14,padding:'8px 16px'}}>{report.conforms?'✓ Conforms':'✗ Violations'}</div>}
      </div>
      {loading&&<p>Validating…</p>}
      {report&&report.entries?.length>0?<div className="glass-card" style={{padding:0,overflow:'hidden'}}><table className="data-table"><thead><tr><th>Severity</th><th>Focus Node</th><th>Path</th><th>Message</th></tr></thead>
        <tbody>{report.entries.map((e:any,i:number)=><tr key={i}><td><span className="badge warn">{e.severity?.split('#').pop()}</span></td><td style={{fontFamily:'var(--mono)',fontSize:11,wordBreak:'break-all'}}>{e.focusNode}</td><td style={{fontFamily:'var(--mono)',fontSize:11}}>{e.path}</td><td>{e.message}</td></tr>)}</tbody></table></div>
      :report&&<div className="glass-card"><CheckCircle2 color="var(--good)" size={20} style={{display:'inline',verticalAlign:'middle',marginRight:8}}/>All data conforms to AICTE shapes. No violations detected.</div>}
    </div>
  );
}

/* ── Lineage ───────────────────────────────────────────────── */
function Lineage({ health }: { health: any }) {
  const [extraNodes, setExtraNodes] = useState<string[]>([]);

  useEffect(() => {
    if (health?.totalTriples > 1408) {
      const count = Math.max(1, Math.floor((health.totalTriples - 1408) / 300 + 1));
      setExtraNodes(Array.from({ length: Math.min(count, 10) }, (_, i) => `u${5 + i}`));
    }
  }, [health?.totalTriples]);

  const allNodes: any[] = [
    {id:'aicte',position:{x:400,y:20},data:{label:'🏛 AICTE Central Ontology'},type:'input'},
    {id:'u1',position:{x:50,y:120},data:{label:'🎓 University 1'},type:'input'},
    {id:'u2',position:{x:250,y:120},data:{label:'🎓 University 2'},type:'input'},
    {id:'u3',position:{x:450,y:120},data:{label:'🎓 University 3'},type:'input'},
    {id:'u4',position:{x:650,y:120},data:{label:'🎓 University 4'},type:'input'},
    ...extraNodes.map((id, i) => ({
      id, position: { x: 850 + i * 200, y: 120 }, data: { label: `🎓 ${id.toUpperCase()} (SQL)` }, type: 'input'
    })),
    {id:'merge',position:{x:350,y:240},data:{label:'📦 Merged Graph'},type:'default'},
    {id:'rules',position:{x:100,y:240},data:{label:'⚙️ Alignment Rules'},type:'input'},
    {id:'inf',position:{x:350,y:360},data:{label:'🧠 Inferred AICTE Graph'},type:'output'},
    {id:'shacl',position:{x:600,y:360},data:{label:'✅ SHACL Validation'},type:'output'}
  ];

  const allEdges: any[] = [
    {id:'e1',source:'u1',target:'merge',animated:true},{id:'e2',source:'u2',target:'merge',animated:true},
    {id:'e3',source:'u3',target:'merge',animated:true},{id:'e4',source:'u4',target:'merge',animated:true},
    {id:'e5',source:'aicte',target:'merge',animated:true,style:{stroke:'var(--accent)'}},
    {id:'e6',source:'merge',target:'inf',animated:true},{id:'e7',source:'rules',target:'inf',animated:true,style:{stroke:'var(--purple)'}},
    {id:'e8',source:'inf',target:'shacl',animated:true,style:{stroke:'var(--good)'}},
    ...extraNodes.map((id, i) => ({id:`e${9+i}`,source:id,target:'merge',animated:true,style:{stroke:'var(--blue)'}}))
  ];

  const [nodes,,onNC] = useNodesState(allNodes);
  const [edges,,onEC] = useEdgesState(allEdges);

  return (
    <div style={{display:'flex',flexDirection:'column',gap:20,height:'calc(100vh - 120px)'}}>
      <div><h2>Data Lineage & Provenance</h2><p className="text-secondary" style={{marginTop:4}}>Visual DAG showing how source ontologies merge into the unified AICTE knowledge graph.</p></div>
      <div className="glass-card" style={{flex:1,padding:0,overflow:'hidden'}}>
        <ReactFlow key={extraNodes.length} nodes={nodes} edges={edges} onNodesChange={onNC} onEdgesChange={onEC} fitView colorMode="dark"><Background color="var(--line-strong)" gap={20}/><Controls/></ReactFlow>
      </div>
    </div>
  );
}
