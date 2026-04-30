export const API_BASE = '/api/v1';

let token = localStorage.getItem('semlink_token');

export const setAuthToken = (newToken: string) => {
  token = newToken;
  if (newToken) {
    localStorage.setItem('semlink_token', newToken);
  } else {
    localStorage.removeItem('semlink_token');
  }
};

const authHeader = (): Record<string, string> => token ? { 'Authorization': `Bearer ${token}` } : {};

export const login = async (username: string, password: string) => {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  if (!res.ok) throw new Error("Login failed");
  const data = await res.json();
  setAuthToken(data.token);
  return data;
};

export const getHealth = async () => {
  const res = await fetch(`${API_BASE}/health`, { headers: authHeader() });
  return res.json();
};

export const runPipeline = async () => {
  const res = await fetch(`${API_BASE}/pipeline/run`, {
    method: 'POST',
    headers: authHeader()
  });
  return res.json();
};

export const executeSparql = async (sparql: string) => {
  const res = await fetch(`${API_BASE}/query/sparql`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ sparql })
  });
  if (!res.ok) {
      if(res.status === 429) {
          throw new Error("Rate limit exceeded. Please wait a minute before querying again.");
      }
      throw new Error("Query execution failed");
  }
  return res.json();
};

export const naturalLanguageQuery = async (question: string) => {
  const res = await fetch(`${API_BASE}/query/natural`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ question })
  });
  if (res.status === 401 || res.status === 403) {
    setAuthToken('');
    window.location.reload();
  }
  if (!res.ok) throw new Error("NL Query failed");
  return res.json();
};

export const getConnections = async () => {
  const res = await fetch(`${API_BASE}/connections`, { headers: authHeader() });
  return res.json();
};

export const addConnection = async (config: any) => {
  const res = await fetch(`${API_BASE}/connections`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(config)
  });
  return res.json();
};

export const removeConnection = async (id: string) => {
  await fetch(`${API_BASE}/connections/${id}`, {
    method: 'DELETE',
    headers: authHeader()
  });
};

export const runValidation = async () => {
  const res = await fetch(`${API_BASE}/validate`, {
    method: 'POST',
    headers: authHeader()
  });
  return res.json();
};

export const getMappings = async () => {
  const res = await fetch(`${API_BASE}/mappings`, { headers: authHeader() });
  return res.json();
};

export const runR2o = async (exampleName: string, mode: string) => {
  const res = await fetch(`${API_BASE}/onboard/r2o`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ exampleName, mode })
  });
  return res.json();
};

export const onboardFromSql = async (name: string, schemaSql: string, dataSql: string) => {
  const res = await fetch(`${API_BASE}/onboard/sql`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, schemaSql, dataSql })
  });
  const data = await res.json();
  if (data.status === 'error') throw new Error(data.message || 'Onboarding failed');
  return data;
};

export const onboardParse = async (name: string, schemaSql: string, dataSql: string) => {
  const res = await fetch(`${API_BASE}/onboard/parse`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, schemaSql, dataSql })
  });
  return res.json();
};

export const onboardSuggest = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/suggest`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name })
  });
  return res.json();
};

export const onboardApprove = async (name: string, approvedMappings: ApprovedMapping[]) => {
  const res = await fetch(`${API_BASE}/onboard/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, approvedMappings })
  });
  return res.json();
};

export const onboardTransform = async (name: string, schemaSql: string, dataSql: string) => {
  const res = await fetch(`${API_BASE}/onboard/transform`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name, schemaSql, dataSql })
  });
  return res.json();
};

export const onboardValidate = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name })
  });
  return res.json();
};

export const onboardPublish = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify({ name })
  });
  return res.json();
};

export const onboardStatus = async (name: string) => {
  const res = await fetch(`${API_BASE}/onboard/status/${name}`, { headers: authHeader() });
  return res.json();
};

export type ApprovedMapping = {
  sourceTable: string;
  sourceColumn: string;
  aicteClass: string;
  aicteProperty: string;
  userCustomized: boolean;
};

export type MappingSuggestion = {
  sourceTable: string;
  sourceColumn: string;
  suggestedClass: string;
  suggestedProperty: string;
  confidence: number;
  rationale: string;
  matchType: string;
};
