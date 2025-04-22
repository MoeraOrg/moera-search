CREATE CONSTRAINT pending_update_id FOR (pu:PendingUpdate) REQUIRE pu.id IS UNIQUE;
