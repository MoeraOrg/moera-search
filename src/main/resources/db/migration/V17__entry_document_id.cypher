CREATE CONSTRAINT entry_document_id IF NOT EXISTS FOR (e:Entry) REQUIRE e.documentId IS UNIQUE;
