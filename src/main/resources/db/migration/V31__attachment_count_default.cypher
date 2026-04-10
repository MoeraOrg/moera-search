MATCH (e:Entry)
WHERE e.attachmentCount IS NULL
SET e.attachmentCount = 0;
