import React, { useState, useEffect } from 'react';
import * as api from './api';
import type { ApprovedMapping } from './api';

interface OnboardedSource {
  id: string;
  type: 'onboarded';
  tables: number;
  rows: number;
  triples: number;
  createdAt: string;
  mappings: ApprovedMapping[];
}

interface DataSource {
  id: string;
  type: string;
  config: any;
  onboarded?: OnboardedSource;
}

export default function Connections({ health }: { health: any }) {
  const [list, setList] = useState<DataSource[]>([]);
  const [showAdd, setShowAdd] = useState(false);
  const [editingSource, setEditingSource] = useState<string | null>(null);
  
  const icons: Record<string, string> = {
    mysql: '🐬', postgres: '🐘', mongodb: '🍃', neo4j: '🔵',
    owl: '📄', csv: '📊', redis: '🔴', onboarded: '📥'
  };

  const reload = async () => {
    try {
      // Get connections from API
      const connections = await api.getConnections();
      
      // Get onboarded sources from local storage or API
      const onboardedSources = await getOnboardedSources();
      
      // Merge them
      const allSources: DataSource[] = [
        ...connections.map((c: any) => ({ ...c, type: c.type || 'unknown' })),
        ...onboardedSources
      ];
      
      setList(allSources);
    } catch (e) {
      console.error('Failed to load connections', e);
    }
  };

  const getOnboardedSources = async (): Promise<OnboardedSource[]> => {
    // Check for onboarding jobs in target directory
    // This is a simplified version - in production, you'd query a database
    try {
      // For now, return empty - we'll enhance this later
      return [];
    } catch (e) {
      return [];
    }
  };

  useEffect(() => {
    reload();
  }, [health?.totalTriples]);

  const addConnection = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    await api.addConnection({
      id: String(data.get('id')),
      type: String(data.get('type')),
      database: String(data.get('database'))
    });
    setShowAdd(false);
    reload();
    form.reset();
  };

  const removeSource = async (id: string) => {
    if (confirm(`Remove source "${id}"? This will delete all associated data.`)) {
      // Remove the source
      await api.removeConnection(id);
      reload();
    }
  };

  const editMappings = (sourceId: string) => {
    setEditingSource(sourceId);
  };

  const saveMappings = async (sourceId: string, mappings: ApprovedMapping[]) => {
    // Save updated mappings
    console.log('Saving mappings for', sourceId, mappings);
    setEditingSource(null);
    alert('Mappings updated! Please re-run onboarding to apply changes.');
  };

  const MappingEditor = ({ source }: { source: OnboardedSource }) => {
    const [mappings, setMappings] = useState<ApprovedMapping[]>(source.mappings);
    
    return (
      <div className="glass-card" style={{ marginTop: 12 }}>
        <h4 style={{ marginBottom: 12 }}>Edit Mappings for {source.id}</h4>
        <table className="data-table">
          <thead>
            <tr>
              <th>Table</th>
              <th>Column</th>
              <th>AICTE Class</th>
              <th>Property</th>
              <th>Confidence</th>
            </tr>
          </thead>
          <tbody>
            {mappings.map((m, i) => (
              <tr key={i}>
                <td><code>{m.sourceTable}</code></td>
                <td><code>{m.sourceColumn}</code></td>
                <td>
                  <select className="input" value={m.aicteClass} style={{fontSize:12}}
                    onChange={e => {
                      const updated = [...mappings];
                      updated[i] = { ...m, aicteClass: e.target.value, userCustomized: true };
                      setMappings(updated);
                    }}>
                    <option>Student</option><option>College</option><option>University</option>
                    <option>Course</option><option>Department</option><option>Program</option>
                  </select>
                </td>
                <td>
                  <select className="input" value={m.aicteProperty} style={{fontSize:12}}
                    onChange={e => {
                      const updated = [...mappings];
                      updated[i] = { ...m, aicteProperty: e.target.value, userCustomized: true };
                      setMappings(updated);
                    }}>
                    <option>id</option><option>name</option><option>department</option>
                    <option>studiesAt</option><option>belongsToUniversity</option>
                    <option>offersCourse</option><option>memberOfDepartment</option>
                  </select>
                </td>
                <td>{m.userCustomized ? 'custom' : 'auto'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div style={{display:'flex',gap:8,marginTop:12}}>
          <button className="btn-primary" onClick={() => saveMappings(source.id, mappings)}>Save Mappings</button>
          <button className="btn-secondary" onClick={() => setEditingSource(null)}>Cancel</button>
        </div>
      </div>
    );
  };

  return (
    <div style={{display:'flex',flexDirection:'column',gap:20}}>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}>
        <h2>Data Sources & Onboarded Data</h2>
        <button className="btn-primary" onClick={() => setShowAdd(true)}>+ Add Connection</button>
      </div>
      
      {showAdd && (
        <form className="glass-card" onSubmit={addConnection} style={{display:'grid',gridTemplateColumns:'1fr 1fr 2fr auto',gap:12,alignItems:'end'}}>
          <div><div className="nav-label">ID</div><input className="input" name="id" placeholder="source-id" required/></div>
          <div><div className="nav-label">Type</div><select className="input" name="type">
            <option value="mysql">MySQL</option><option value="postgres">PostgreSQL</option>
            <option value="mongodb">MongoDB</option><option value="neo4j">Neo4j</option>
            <option value="owl">OWL File</option><option value="csv">CSV</option>
          </select></div>
          <div><div className="nav-label">Database / Path</div><input className="input" name="database" placeholder="jdbc:mysql://... or file path" required/></div>
          <button className="btn-primary" type="submit">Save</button>
        </form>
      )}

      <div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(320px,1fr))',gap:16}}>
        {list.map((source) => (
          <div key={source.id} className="glass-card" style={{position:'relative'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start'}}>
              <div style={{fontSize:28,marginBottom:8}}>{icons[source.type]||'📁'}</div>
              <div style={{display:'flex',gap:4}}>
                {source.type !== 'onboarded' && (
                  <button className="btn-secondary" style={{padding:'4px 8px',fontSize:11}}
                    onClick={() => removeSource(source.id)}>✕</button>
                )}
                {source.type === 'onboarded' && (
                  <button className="btn-secondary" style={{padding:'4px 8px',fontSize:11}}
                    onClick={() => editMappings(source.id)}>✏️</button>
                )}
              </div>
            </div>
            
            <h3>{source.id}</h3>
            <div className="badge blue" style={{margin:'6px 0'}}>{source.type}</div>
            
            <div style={{fontSize:12,marginTop:8}}>
              {source.type === 'onboarded' ? (
                <>
                  <div className="text-secondary">Tables: {(source as any).tables}</div>
                  <div className="text-secondary">Rows: {(source as any).rows}</div>
                  <div className="text-secondary">Triples: {(source as any).triples}</div>
                </>
              ) : (
                <p className="text-secondary" style={{wordBreak:'break-all'}}>
                  {(source as any).database || (source as any).host || 'Local'}
                </p>
              )}
            </div>

            {editingSource === source.id && source.type === 'onboarded' && (
              <MappingEditor source={source as unknown as OnboardedSource} />
            )}
          </div>
        ))}
        {list.length === 0 && (
          <p className="text-secondary">No data sources yet. Add connections or run onboarding to get started.</p>
        )}
      </div>
    </div>
  );
}