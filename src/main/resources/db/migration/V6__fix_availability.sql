UPDATE users SET is_available = true  WHERE is_available IS NULL AND role = 'DRIVER';
UPDATE users SET is_available = false WHERE is_available IS NULL AND role = 'RIDER';