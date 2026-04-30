# Healthcare Compliance Workflow

## Purpose

Show how SEMLINK can route compliance-aware healthcare queries across clinical, imaging, and genomics systems.

## Sources

| Source | Native model | Example data |
| --- | --- | --- |
| Oracle SQL | Relational | Patient records and encounters |
| S3/NoSQL | Document/object metadata | Imaging studies and reports |
| Specialized genomics DB | Wide-column or domain store | Variants and markers |
| Policy service | KV/rules | Consent and role permissions |

## SEMLINK Query

```sparql
PREFIX health: <https://semlink.example.org/health#>

SELECT ?patient ?riskMarker ?study ?allowedField WHERE {
  ?patient a health:Patient ;
    health:hasCondition "Diabetes" ;
    health:hasGenomicMarker ?riskMarker ;
    health:hasImagingStudy ?study .
  ?study health:abnormalFinding true .
  ?patient health:followUpStatus "Incomplete" .
  ?allowedField health:visibleToRole "CareCoordinator" .
}
```

## Wow Moment

The result is clinically useful but policy-aware: restricted fields are masked and every row explains the source and access rule.
