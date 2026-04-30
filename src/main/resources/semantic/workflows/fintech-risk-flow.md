# Fintech Risk Workflow

## Purpose

Show how SEMLINK can combine transactions, fraud graph proximity, cached signals, and warehouse reporting for real-time risk scoring.

## Sources

| Source | Native model | Example data |
| --- | --- | --- |
| MySQL | Relational | Transactions and accounts |
| Redis | Key-value | Device and velocity signals |
| Graph DB | Property graph | Fraud rings and account links |
| Data warehouse | Analytical relational | Regulatory exposure reports |

## SEMLINK Query

```sparql
PREFIX risk: <https://semlink.example.org/risk#>

SELECT ?transaction ?account ?amount ?riskScore ?reason WHERE {
  ?transaction a risk:Transaction ;
    risk:account ?account ;
    risk:amount ?amount ;
    risk:hasFraudSignal ?signal .
  ?account risk:graphDistanceToFraudRing ?distance ;
    risk:regulatoryExposure "High" .
  ?signal risk:deviceRisk "Elevated" .
  BIND((100 - (?distance * 10)) AS ?riskScore)
  FILTER(?amount > 50000)
}
ORDER BY DESC(?riskScore)
```

## Wow Moment

The demo shows one semantic risk answer composed from a transaction table, Redis fraud signal, graph neighborhood, and warehouse exposure report.
