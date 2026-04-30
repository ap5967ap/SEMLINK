import React, { useState } from 'react';
import type { MappingSuggestion, ApprovedMapping } from './api';

const AICTE_PROPERTIES = [
  'id', 'name', 'cgpa', 'department',
  'studiesAt', 'belongsToUniversity', 'offersCourse', 'memberOfDepartment', 'enrolledIn'
];
const AICTE_CLASSES = ['Student', 'College', 'University', 'Course', 'Department', 'Program'];

type Props = {
  suggestions: MappingSuggestion[];
  onApprove: (mappings: ApprovedMapping[]) => void;
  onBack: () => void;
};

export default function MappingReviewTable({ suggestions, onApprove, onBack }: Props) {
  const [pending, setPending] = useState<Map<string, string>>(() => {
    const initial = new Map<string, string>();
    suggestions.forEach(s => {
      initial.set(`${s.sourceTable}.${s.sourceColumn}`, s.suggestedProperty);
    });
    return initial;
  });
  const [expandedTable, setExpandedTable] = useState<string | null>(null);

  const grouped = suggestions.reduce((acc, s) => {
    acc.set(s.sourceTable, [...(acc.get(s.sourceTable) || []), s]);
    return acc;
  }, new Map<string, MappingSuggestion[]>());

  const setProperty = (table: string, column: string, value: string) => {
    setPending(p => {
      const next = new Map(p);
      next.set(`${table}.${column}`, value);
      return next;
    });
  };

  const handleApprove = () => {
    const approved = suggestions.map(s => ({
      sourceTable: s.sourceTable,
      sourceColumn: s.sourceColumn,
      aicteClass: s.suggestedClass,
      aicteProperty: pending.get(`${s.sourceTable}.${s.sourceColumn}`) || s.suggestedProperty,
      userCustomized: pending.get(`${s.sourceTable}.${s.sourceColumn}`) !== s.suggestedProperty
    }));
    onApprove(approved);
  };

  const confidenceColor = (c: number) =>
    c >= 0.85 ? 'var(--good)' : c >= 0.65 ? 'var(--warn)' : 'var(--bad)';

  const confidenceLabel = (c: number) =>
    c >= 0.85 ? '\u2713' : c >= 0.65 ? '\u26A0' : '\u2717';

  const approvedCount = Array.from(pending.entries()).filter(([key]) => {
    const [table, col] = key.split('.');
    const orig = grouped.get(table)?.find(s => s.sourceColumn === col);
    return orig !== undefined;
  }).length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h3>Review Mapping Suggestions</h3>
          <p className="text-secondary">
            {suggestions.length} suggestions across {grouped.size} tables.
            Review and adjust each mapping, then approve to continue.
          </p>
          <p className="text-secondary" style={{ fontSize: 12, marginTop: 4 }}>
            <span style={{ color: 'var(--good)' }}>{'\u25CF'} High</span> &ge;0.85 &nbsp;
            <span style={{ color: 'var(--warn)' }}>{'\u25CF'} Medium</span> 0.65-0.84 &nbsp;
            <span style={{ color: 'var(--bad)' }}>{'\u25CF'} Low</span> &lt;0.65 &nbsp;
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn-secondary" onClick={onBack}>&larr; Back to Upload</button>
        </div>
      </div>

      {Array.from(grouped.entries()).map(([tableName, cols]) => (
        <div key={tableName} className="glass-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div
            style={{
              padding: '12px 16px', cursor: 'pointer', display: 'flex', justifyContent: 'space-between',
              background: 'var(--panel)',
              borderBottom: expandedTable === tableName ? '1px solid var(--line)' : 'none'
            }}
            onClick={() => setExpandedTable(expandedTable === tableName ? null : tableName)}
          >
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <span style={{ fontSize: 11, color: 'var(--muted)' }}>
                {expandedTable === tableName ? '\u25BC' : '\u25B6'}
              </span>
              <span style={{ fontWeight: 600 }}>{tableName}</span>
              <span className="badge blue">{cols.length} columns</span>
            </div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              {cols.map(c => (
                <div key={c.sourceColumn} style={{
                  width: 8, height: 8, borderRadius: '50%',
                  background: confidenceColor(c.confidence)
                }} title={`${c.sourceColumn}: ${c.confidence.toFixed(2)}`} />
              ))}
            </div>
          </div>

          {expandedTable === tableName && (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ margin: 0 }}>
                <thead>
                  <tr>
                    <th>Source Column</th>
                    <th>AICTE Class</th>
                    <th>Target Property</th>
                    <th>Confidence</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {cols.map(s => {
                    const currentVal = pending.get(`${tableName}.${s.sourceColumn}`) || s.suggestedProperty;
                    const changed = currentVal !== s.suggestedProperty;
                    return (
                      <tr key={s.sourceColumn} style={changed ? { background: 'rgba(124,58,237,0.08)' } : {}}>
                        <td>
                          <code style={{ fontFamily: 'var(--mono)' }}>{s.sourceColumn}</code>
                          {changed && <span className="badge purple" style={{ marginLeft: 6, fontSize: 10 }}>edited</span>}
                        </td>
                        <td>
                          <select className="input" value={s.suggestedClass}
                            style={{ fontSize: 12, minWidth: 120 }}
                            onChange={e => { /* reserved */ }}>
                            {AICTE_CLASSES.map(c => <option key={c} value={c}>{c}</option>)}
                          </select>
                        </td>
                        <td>
                          <select className="input" value={currentVal}
                            style={{ fontSize: 12, minWidth: 160 }}
                            onChange={e => setProperty(tableName, s.sourceColumn, e.target.value)}>
                            {AICTE_PROPERTIES.map(p => (
                              <option key={p} value={p}>{p}</option>
                            ))}
                          </select>
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <div style={{
                              width: 50, height: 6, background: 'var(--line)', borderRadius: 3
                            }}>
                              <div style={{
                                width: `${s.confidence * 100}%`, height: '100%',
                                background: confidenceColor(s.confidence), borderRadius: 3
                              }} />
                            </div>
                            <span style={{ fontSize: 12, color: confidenceColor(s.confidence) }}>
                              {confidenceLabel(s.confidence)} {s.confidence.toFixed(2)}
                            </span>
                          </div>
                        </td>
                        <td>
                          <button className="btn-secondary" style={{ fontSize: 11, padding: '3px 8px' }}
                            onClick={() => setProperty(tableName, s.sourceColumn, s.suggestedProperty)}>
                            Reset
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {expandedTable === tableName && cols.some(c => c.rationale) && (
            <div style={{ padding: '10px 16px', borderTop: '1px solid var(--line)' }}>
              <div className="nav-label" style={{ marginBottom: 4 }}>Rationale</div>
              {cols.filter(c => c.rationale).map(c => (
                <div key={c.sourceColumn} style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 2 }}>
                  <strong>{c.sourceColumn}</strong>: {c.rationale}
                </div>
              ))}
            </div>
          )}
        </div>
      ))}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        className="glass-card">
        <span className="text-secondary">{approvedCount} of {suggestions.length} mappings ready</span>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn-secondary" onClick={onBack}>&larr; Cancel</button>
          <button className="btn-primary" onClick={handleApprove}>
            Approve All &amp; Transform &rarr;
          </button>
        </div>
      </div>
    </div>
  );
}