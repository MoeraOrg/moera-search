MATCH (m:Machine)<-[r:DONE_BY]-(f:Favor)
SET f.machineActorMigration = true
DELETE r;
MATCH (m:Machine)
DELETE m;
CREATE (:Machine);
MATCH (m:Machine), (f:Favor {machineActorMigration: true})
MERGE (f)-[:DONE_BY]->(m)
REMOVE f.machineActorMigration;
