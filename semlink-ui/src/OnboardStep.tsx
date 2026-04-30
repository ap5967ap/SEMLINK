import React, { useState, useRef } from 'react';
import type { MappingSuggestion, ApprovedMapping } from './api';
import * as api from './api';
import MappingReviewTable from './MappingReviewTable';

type Step = 'UPLOAD' | 'PARSING' | 'SUGGESTING' | 'REVIEWING' | 'TRANSFORMING' | 'VALIDATING' | 'PUBLISHING' | 'DONE' | 'ERROR';

const STEP_LABELS = ['Extract', 'Map & LLM', 'Review', 'Transform', 'Validate', 'Publish'];

const delay = (ms: number) => new Promise(r => setTimeout(r, ms));

interface OnboardStepProps {
  onComplete: () => void;
}

export default function OnboardStep({ onComplete }: OnboardStepProps) {
  const [step, setStep] = useState<Step>('UPLOAD');
  const [logs, setLogs] = useState<string[]>([]);
  const [schemaSql, setSchemaSql] = useState('');
  const [dataSql, setDataSql] = useState('');
  const [datasetName] = useState(() => 'university-' + Date.now());
  const [suggestions, setSuggestions] = useState<MappingSuggestion[]>([]);
  const [llmMode, setLlmMode] = useState('');
  const [transformResult, setTransformResult] = useState<any>(null);
  const [error, setError] = useState('');
  const logRef = useRef<HTMLDivElement>(null);

  const addLog = (msg: string) => {
    setLogs(p => [...p, msg]);
    setTimeout(() => logRef.current?.scrollTo(0, 9999), 50);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>, setter: (v: string) => void) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (ev) => setter((ev.target?.result as string) || '');
      reader.readAsText(file);
    }
  };

  const startOnboarding = async () => {
    if (!schemaSql || !dataSql) {
      alert('Please provide both Schema and Data SQL files');
      return;
    }
    try {
      setStep('PARSING');
      setLogs([]);
      addLog('\u25B6 Step 1: Parsing Source Database (SQL Schema + Data)\u2026');
      await delay(400);

      const parseResult = await api.onboardParse(datasetName, schemaSql, dataSql);
      if (parseResult.status !== 'parsed') throw new Error(parseResult.message || 'Parse failed');
      addLog(`  \u2713 Found ${parseResult.tables} tables and ${parseResult.totalRows} rows.`);
      addLog('  \u2713 Schema parsed successfully.');

      setStep('SUGGESTING');
      addLog('\u25B6 Step 2: Generating LLM Mapping Suggestions\u2026');
      await delay(400);

      const suggestResult = await api.onboardSuggest(datasetName);
      if (suggestResult.status !== 'completed') throw new Error(suggestResult.message || 'Suggest failed');
      setSuggestions(suggestResult.suggestions || []);
      setLlmMode(suggestResult.llmMode || 'heuristic');
      addLog(`  \u2713 LLM (${suggestResult.llmMode}) generated ${suggestResult.suggestions?.length || 0} suggestions.`);
      addLog('  \u2713 Review the suggestions in the next step.');

      setStep('REVIEWING');
    } catch (err: any) {
      setStep('ERROR');
      setError(err.message);
      addLog(`  \u274C Error: ${err.message}`);
    }
  };

  const handleApprove = async (approved: ApprovedMapping[]) => {
    try {
      addLog('\u25B6 Step 3: Applying Approved Mappings\u2026');
      const approveResult = await api.onboardApprove(datasetName, approved);
      if (approveResult.status !== 'approved') throw new Error(approveResult.message);
      addLog(`  \u2713 ${approveResult.mappingCount} mappings approved.`);

      setStep('TRANSFORMING');
      addLog('\u25B6 Step 4: Transforming SQL to RDF triples\u2026');
      await delay(400);

      const trResult = await api.onboardTransform(datasetName, schemaSql, dataSql);
      if (trResult.status !== 'completed') throw new Error(trResult.message);
      setTransformResult(trResult);
      addLog(`  \u2713 Generated ${trResult.triples} semantic triples.`);
      if (trResult.extractedClasses?.length) {
        addLog(`  \u2713 Extracted Classes: ${trResult.extractedClasses.join(', ')}`);
      }

      setStep('VALIDATING');
      addLog('\u25B6 Step 5: Validating output triples\u2026');
      await delay(400);

      const validateResult = await api.onboardValidate(datasetName);
      if (validateResult.status !== 'completed') throw new Error(validateResult.message);
      addLog(validateResult.conforms
        ? '  \u2713 Validation passed \u2014 triples conform to AICTE shapes.'
        : `  \u26A0 ${validateResult.violationCount} validation warnings.`);

      setStep('PUBLISHING');
      addLog('\u25B6 Step 6: Publishing to unified graph\u2026');
      await delay(400);

      const publishResult = await api.onboardPublish(datasetName);
      if (publishResult.status !== 'published') throw new Error(publishResult.message);
      addLog(`  \u2713 Published! New Graph Size: ${publishResult.totalTriples} triples.`);
      addLog(`  \u2713 Added node "${publishResult.newNodeLabel}" to lineage graph.`);
      addLog('\u2705 Onboarding complete! Your SQL data is now mapped and queryable in the semantic graph.');

      setStep('DONE');
      onComplete();
    } catch (err: any) {
      setStep('ERROR');
      setError(err.message);
      addLog(`  \u274C Error: ${err.message}`);
    }
  };

  const handleBackToReview = async () => {
    setStep('REVIEWING');
    addLog('\u21A9 Reloading mapping suggestions\u2026');
    try {
      const suggestResult = await api.onboardSuggest(datasetName);
      setSuggestions(suggestResult.suggestions || []);
    } catch (err: any) {
      addLog(`  \u26A0 Could not reload suggestions: ${err.message}`);
    }
  };

  const stepIndex = ['UPLOAD','PARSING'].includes(step) ? 0
    : step === 'SUGGESTING' ? 1
    : step === 'REVIEWING' ? 2
    : step === 'TRANSFORMING' ? 3
    : step === 'VALIDATING' ? 4
    : ['PUBLISHING','DONE'].includes(step) ? 5
    : 5;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 950 }}>
      <div>
        <h2>RDB &rarr; Ontology Onboarding</h2>
        <p className="text-secondary" style={{ marginTop: 4 }}>
          Upload SQL files to automatically map your relational data to the AICTE ontology
          with LLM-assisted mapping suggestions and human review.
        </p>
      </div>

      <div className="step-track">
        {STEP_LABELS.map((label, i) => (
          <div key={label} className={`step-item ${i < stepIndex ? 'done' : i === stepIndex ? 'active' : ''}`}>
            <div className="step-num">{i < stepIndex ? '\u2713' : i + 1}</div>
            <div className="step-name">{label}</div>
          </div>
        ))}
      </div>

      {step === 'UPLOAD' && (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <div className="nav-label">Schema SQL File</div>
              <input type="file" accept=".sql" className="input"
                onChange={e => handleFileUpload(e, setSchemaSql)} />
              {schemaSql && <div style={{ fontSize: 11, color: 'var(--good)', marginTop: 4 }}>
                \u2713 Schema loaded ({schemaSql.length.toLocaleString()} bytes)</div>}
            </div>
            <div>
              <div className="nav-label">Data SQL File</div>
              <input type="file" accept=".sql" className="input"
                onChange={e => handleFileUpload(e, setDataSql)} />
              {dataSql && <div style={{ fontSize: 11, color: 'var(--good)', marginTop: 4 }}>
                \u2713 Data loaded ({dataSql.length.toLocaleString()} bytes)</div>}
            </div>
          </div>
          <button className="btn-primary" onClick={startOnboarding}
            disabled={!schemaSql || !dataSql} style={{ alignSelf: 'flex-start' }}>
            Start Onboarding &rarr;
          </button>
        </div>
      )}

      {['PARSING','SUGGESTING','TRANSFORMING','VALIDATING','PUBLISHING'].includes(step) && (
        <div className="glass-card" style={{
          display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center', padding: 40
        }}>
          <div style={{
            width: 36, height: 36, border: '3px solid var(--line)', borderTopColor: 'var(--accent)',
            borderRadius: '50%', animation: 'spin 0.8s linear infinite'
          }} />
          <p>{
            step === 'PARSING' ? 'Parsing SQL schema and data\u2026' :
            step === 'SUGGESTING' ? 'Generating LLM mapping suggestions\u2026' :
            step === 'TRANSFORMING' ? 'Applying mappings and generating RDF triples\u2026' :
            step === 'VALIDATING' ? 'Validating against SHACL shapes\u2026' :
            'Publishing to unified graph\u2026'
          }</p>
          <p className="text-secondary" style={{ fontSize: 12 }}>This may take a moment{(step === 'SUGGESTING' && llmMode === 'gemini') ? ' (LLM API call)' : ''}</p>
        </div>
      )}

      {step === 'REVIEWING' && (
        <MappingReviewTable
          suggestions={suggestions}
          onApprove={handleApprove}
          onBack={() => { setStep('UPLOAD'); setLogs([]); }}
        />
      )}

      {logs.length > 0 && (
        <div className="workflow-log" ref={logRef}>
          {logs.map((l, i) => (
            <div key={i} className={`log-line ${
              l.startsWith('\u2705') ? 'success' :
              l.startsWith('\u25B6') ? 'info' :
              l.startsWith('  \u274C') ? 'bad' : ''
            }`}>{l}</div>
          ))}
        </div>
      )}

      {step === 'ERROR' && (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="banner bad">\u274C {error}</div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-secondary" onClick={() => {
              setStep('UPLOAD'); setLogs([]); setError(''); setSuggestions([]);
            }}>&larr; Start Over</button>
            <button className="btn-secondary" onClick={handleBackToReview}>&larr; Back to Review</button>
          </div>
        </div>
      )}

      {step === 'DONE' && (
        <div className="glass-card">
          <div className="banner good">\u2705 Onboarding complete! View the Lineage tab to see the new connection.</div>
          {transformResult && (
            <div style={{ marginTop: 16, display: 'flex', gap: 16 }}>
              <div className="stat-card">
                <div className="stat-label">Triples Generated</div>
                <div className="stat-value">{transformResult.triples}</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Classes Extracted</div>
                <div className="stat-value">{transformResult.extractedClasses?.length || 0}</div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}