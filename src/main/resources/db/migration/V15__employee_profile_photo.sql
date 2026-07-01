-- Optional profile image: HTTPS URL or data:image/...;base64,... (local upload)
ALTER TABLE employees
    ADD COLUMN profile_photo_url MEDIUMTEXT NULL;
