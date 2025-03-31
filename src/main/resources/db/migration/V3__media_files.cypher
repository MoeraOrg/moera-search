CREATE CONSTRAINT media_file_id FOR (mf:MediaFile) REQUIRE mf.id IS UNIQUE;
