import {
  Activity,
  Braces,
  Cable,
  CheckCircle2,
  Database,
  GitBranch,
  Network,
  Route,
  ShieldCheck,
  Sparkles,
  Workflow,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

export type ViewKey =
  | "dashboard"
  | "connections"
  | "explore"
  | "query"
  | "validate"
  | "onboard"
  | "lineage"
  | "usecases";

export type Source = {
  id: string;
  name: string;
  type: string;
  nativeModel: string;
  status: "healthy" | "warning" | "critical";
  quality: number;
  triples: number;
  entities: number;
  endpoint: string;
  icon: LucideIcon;
};

export type QueryRow = Record<string, string | number>;

export type QueryDemo = {
  id: string;
  title: string;
  nativeQueries: string[];
  sparql: string;
  columns: string[];
  rows: QueryRow[];
  lesson: string;
  wow: string;
};

export const navigation: Array<{ key: ViewKey; label: string; icon: LucideIcon }> = [
  { key: "dashboard", label: "Dashboard", icon: Activity },
  { key: "connections", label: "Connections", icon: Cable },
  { key: "explore", label: "Explore", icon: Network },
  { key: "query", label: "Query", icon: Braces },
  { key: "validate", label: "Validate", icon: ShieldCheck },
  { key: "onboard", label: "Onboard", icon: Workflow },
  { key: "lineage", label: "Lineage", icon: GitBranch },
  { key: "usecases", label: "Use Cases", icon: Route },
];

export const sources: Source[] = [
  {
    id: "university1",
    name: "University 1",
    type: "JDBC / R2O",
    nativeModel: "Relational",
    status: "healthy",
    quality: 98,
    triples: 693,
    entities: 6,
    endpoint: "http://localhost:3030/university1/sparql",
    icon: Database,
  },
  {
    id: "university2",
    name: "University 2",
    type: "Mongo adapter",
    nativeModel: "Document",
    status: "warning",
    quality: 87,
    triples: 157,
    entities: 5,
    endpoint: "http://localhost:3031/university2/sparql",
    icon: Braces,
  },
  {
    id: "university3",
    name: "University 3",
    type: "Neo4j adapter",
    nativeModel: "Graph",
    status: "critical",
    quality: 61,
    triples: 184,
    entities: 5,
    endpoint: "http://localhost:3032/university3/sparql",
    icon: Network,
  },
  {
    id: "university4",
    name: "University 4",
    type: "Redis / Cassandra",
    nativeModel: "KV / Wide-column",
    status: "warning",
    quality: 72,
    triples: 176,
    entities: 5,
    endpoint: "http://localhost:3033/university4/sparql",
    icon: Database,
  },
];

export const pipelineStages = [
  {
    title: "Connect",
    detail: "Register live source or file adapter",
    command: "connect add --type owl --id university1 --path ...",
  },
  {
    title: "Discover",
    detail: "extractSchema creates SchemaDescriptor",
    command: "pipeline run --source university1",
  },
  {
    title: "Map",
    detail: "R2O, parser facades, SimilarityMatcher",
    command: "r2o assist example-college",
  },
  {
    title: "Validate",
    detail: "SHACL report per source and aggregate",
    command: "validate",
  },
  {
    title: "Query",
    detail: "SPARQL, NL, and SERVICE federation plan",
    command: "query cs_students_by_university",
  },
];

export const schemaExplorer = [
  {
    source: "University 1",
    model: "Relational",
    nodes: ["tbl_students", "tbl_departments", "tbl_courses", "tbl_enrollment"],
    mapsTo: ["aicte:Student", "aicte:Department", "aicte:Course", "aicte:Enrollment"],
  },
  {
    source: "University 2",
    model: "Document",
    nodes: ["students", "dept.ref", "enrollments[]", "metadata.version"],
    mapsTo: ["aicte:Student", "aicte:memberOfDepartment", "aicte:enrolledIn", "prov:Entity"],
  },
  {
    source: "University 3",
    model: "Graph",
    nodes: ["(:Student)", "[:BELONGS_TO]", "(:Course)", "[:TAUGHT_BY]"],
    mapsTo: ["aicte:Student", "aicte:studiesAt", "aicte:Course", "aicte:taughtBy"],
  },
  {
    source: "University 4",
    model: "KV / Wide-column",
    nodes: ["student:{reg_no}:profile", "attendance_by_student", "course_id", "present"],
    mapsTo: ["aicte:Student", "aicte:AttendanceRecord", "aicte:Course", "aicte:attendanceStatus"],
  },
];

export const queries: QueryDemo[] = [
  {
    id: "usecase1",
    title: "AICTE Accreditation Dashboard",
    nativeQueries: [
      "MySQL: SELECT COUNT(*) FROM tbl_students WHERE dept='CSE'",
      "MongoDB: db.students.countDocuments({department:'CS'})",
      "Neo4j: MATCH (s:Student)-[:BELONGS_TO]->(:Dept {name:'Computer Science'}) RETURN COUNT(s)",
      "Redis: SCAN student:*:profile | parse JSON | filter dept",
    ],
    sparql: `PREFIX aicte: <https://semlink.example.org/aicte#>

SELECT ?universityName (COUNT(DISTINCT ?student) AS ?csStudents)
WHERE {
  ?student a aicte:Student ;
    aicte:department ?department ;
    aicte:studiesAt ?college .
  ?college aicte:belongsToUniversity ?university .
  ?university aicte:name ?universityName .
  FILTER(LCASE(STR(?department)) = "computer science")
}
GROUP BY ?universityName
ORDER BY DESC(?csStudents) ?universityName`,
    columns: ["universityName", "csStudents", "provenance"],
    rows: [
      { universityName: "Beacon University", csStudents: 3, provenance: "University2 document + AICTE ontology" },
      { universityName: "Knowledge Grid University", csStudents: 3, provenance: "University4 KV + owl:sameAs" },
      { universityName: "North State University", csStudents: 3, provenance: "University3 graph + rule inference" },
      { universityName: "Institute of Advanced Tech University", csStudents: 2, provenance: "University1 relational R2O" },
    ],
    lesson: "ER model to OWL mapping, owl:sameAs identity resolution, federated-style SPARQL.",
    wow: "One query. Four databases. Unified result with provenance.",
  },
  {
    id: "usecase2",
    title: "Cross-University Student Deduplication",
    nativeQueries: ["SQL name lookup", "Cypher node lookup", "Manual DOB and roll-number comparison"],
    sparql: `PREFIX aicte: <https://semlink.example.org/aicte#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

SELECT ?canonical ?name ?rollNo ?allSources WHERE {
  ?canonical owl:sameAs* ?entity .
  ?entity aicte:name ?name ; aicte:id ?rollNo .
  BIND(STR(?entity) AS ?allSources)
}`,
    columns: ["canonical", "name", "rollNo", "allSources"],
    rows: [
      { canonical: "u3:P003", name: "Asha Verma", rollNo: "P202303", allSources: "u3:P003, u4:SI003" },
      { canonical: "u1:Stud_202301", name: "Student Name 1", rollNo: "R202301", allSources: "u1:Stud_202301, u2:S001" },
    ],
    lesson: "Entity identity, owl:sameAs closure, SimilarityMatcher review.",
    wow: "A canonical student appears with merged attributes across universities.",
  },
  {
    id: "usecase3",
    title: "Data Quality Report For Regulators",
    nativeQueries: ["Manual null checks", "CSV validation", "Separate scripts per source"],
    sparql: `PREFIX sh: <http://www.w3.org/ns/shacl#>
SELECT ?focusNode ?resultPath ?message WHERE {
  ?result a sh:ValidationResult ;
    sh:focusNode ?focusNode ;
    sh:resultPath ?resultPath ;
    sh:resultMessage ?message .
}`,
    columns: ["university", "score", "violations"],
    rows: [
      { university: "University 1", score: "98/100", violations: "0 critical" },
      { university: "University 2", score: "87/100", violations: "13 missing profile fields" },
      { university: "University 3", score: "61/100", violations: "39 invalid dept/cgpa values" },
      { university: "University 4", score: "72/100", violations: "22 non-standard course codes" },
    ],
    lesson: "SHACL constraint modelling and data quality scoring.",
    wow: "Regulators get an explainable quality score per institution.",
  },
  {
    id: "usecase4",
    title: "Natural Language Query Interface",
    nativeQueries: ["Registrar asks a plain-English question", "SPARQL would normally require a technical user"],
    sparql: `PREFIX aicte: <https://semlink.example.org/aicte#>
SELECT ?student ?name ?cgpa ?university WHERE {
  ?student a aicte:Student ;
    aicte:name ?name ;
    aicte:cgpa ?cgpa ;
    aicte:belongsToUniversity ?university .
  FILTER(?cgpa > 9.0)
}`,
    columns: ["student", "name", "cgpa", "university"],
    rows: [],
    lesson: "Ontology-grounded NL-to-SPARQL translation with transparent generated query.",
    wow: "The UI shows the generated SPARQL before execution.",
  },
  {
    id: "usecase5",
    title: "New Institution Onboarding",
    nativeQueries: ["DESCRIBE students", "SHOW CREATE TABLE departments", "Manual mapping spreadsheet"],
    sparql: `PREFIX aicte: <https://semlink.example.org/aicte#>
SELECT ?student ?name WHERE {
  ?student a aicte:Student ;
    aicte:name ?name .
}
LIMIT 10`,
    columns: ["step", "artifact", "status"],
    rows: [
      { step: "Connect", artifact: "connections.json", status: "registered" },
      { step: "Discover", artifact: "SchemaDescriptor", status: "6 tables, 42 columns, 8 FKs" },
      { step: "Map", artifact: "mapping-review.txt", status: "3 high-confidence mappings" },
      { step: "Publish", artifact: "raw-export.ttl", status: "queryable in 47 seconds" },
    ],
    lesson: "R2RML mapping, schema auto-discovery, and self-service onboarding.",
    wow: "University5 becomes queryable without OWL knowledge.",
  },
];

export const validationFindings = [
  { source: "University 1", severity: "Info", issue: "No critical SHACL issues", shape: "aicte:StudentShape" },
  { source: "University 2", severity: "Warning", issue: "13 student profiles missing optional email", shape: "aicte:ContactShape" },
  { source: "University 3", severity: "Critical", issue: "39 invalid department or CGPA values", shape: "aicte:StudentShape" },
  { source: "University 4", severity: "Warning", issue: "22 non-standard course codes", shape: "aicte:CourseShape" },
];

export const onboardingMappings = [
  { source: "learner_id", target: "aicte:id", confidence: 0.89 },
  { source: "full_nm", target: "aicte:name", confidence: 0.94 },
  { source: "brn_cd", target: "aicte:department", confidence: 0.71 },
  { source: "course_ref", target: "aicte:enrolledIn", confidence: 0.82 },
];

export const lineageEdges: [string, string][] = [
  ["tbl_students.reg_no", "u1:Stud_202301"],
  ["u1:Stud_202301", "aicte:Student"],
  ["u3:P003", "owl:sameAs"],
  ["owl:sameAs", "u4:SI003"],
  ["aicte:Student", "cs_students_by_university"],
  ["cs_students_by_university", "AICTE report row"],
];

export const headlineStats = [
  { label: "Connected sources", value: "4", icon: Cable },
  { label: "Inferred students", value: "24", icon: Sparkles },
  { label: "Named queries", value: "12", icon: Braces },
  { label: "Quality gate", value: "SHACL", icon: CheckCircle2 },
];
