-- Dashboard user profile photo (HTTPS URL or data:image/...;base64,...)
ALTER TABLE users
    ADD COLUMN profile_photo_url MEDIUMTEXT NULL;
