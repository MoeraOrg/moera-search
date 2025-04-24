CREATE CONSTRAINT pending_update_id IF NOT EXISTS FOR (pu:PendingUpdate) REQUIRE pu.id IS UNIQUE;
