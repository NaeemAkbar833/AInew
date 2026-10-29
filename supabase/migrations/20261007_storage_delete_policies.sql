-- Migration: Add Missing Storage DELETE Policies for Authenticated Teachers
-- Scope: Only adds FOR DELETE policies on storage.objects for 'question-papers'
--        and 'student-papers', restricted to the authenticated teacher's own
--        first-level folder (<teacher_uuid>/...).
--        Does not modify or recreate existing SELECT, INSERT, or UPDATE policies.

DROP POLICY IF EXISTS "Teachers can delete their own question papers" ON storage.objects;
CREATE POLICY "Teachers can delete their own question papers"
ON storage.objects
FOR DELETE
TO authenticated
USING (
    bucket_id = 'question-papers'
    AND auth.uid() IS NOT NULL
    AND (storage.foldername(name))[1] = auth.uid()::text
);

DROP POLICY IF EXISTS "Teachers can delete their own student papers" ON storage.objects;
CREATE POLICY "Teachers can delete their own student papers"
ON storage.objects
FOR DELETE
TO authenticated
USING (
    bucket_id = 'student-papers'
    AND auth.uid() IS NOT NULL
    AND (storage.foldername(name))[1] = auth.uid()::text
);
