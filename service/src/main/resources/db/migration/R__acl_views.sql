DROP VIEW IF EXISTS person_acl_view;
DROP VIEW IF EXISTS child_acl_view;
DROP VIEW IF EXISTS daycare_group_acl_view;
DROP VIEW IF EXISTS daycare_acl_view;

-- Effective ACL role in a daycare for a user
--
-- Includes both inferred roles and actual configured ACL roles
CREATE VIEW daycare_acl_view(employee_id, daycare_id, role) AS (
    SELECT employee_id, daycare_id, role
    FROM daycare_acl

    UNION ALL

    SELECT id, unit_id, 'MOBILE'
    FROM mobile_device
    WHERE deleted = false AND unit_id IS NOT NULL

    UNION ALL

    SELECT mobile_device.id, daycare_acl.daycare_id, 'MOBILE'
    FROM daycare_acl
    JOIN mobile_device ON daycare_acl.employee_id = mobile_device.employee_id
    WHERE mobile_device.deleted = false
);

CREATE VIEW daycare_group_acl_view(employee_id, daycare_group_id, role) AS (
    SELECT employee_id, dp.id, role
    FROM daycare_group dp
    JOIN daycare_acl_view USING (daycare_id)

    UNION ALL

    SELECT employee_id, daycare_group_id, 'GROUP_STAFF'
    FROM daycare_group_acl acl
);

CREATE VIEW child_acl_view(employee_id, child_id, role) AS (
    SELECT employee_id, pl.child_id, role
    FROM placement pl
    JOIN daycare_acl_view acl ON pl.unit_id = acl.daycare_id
    WHERE pl.end_date > current_date - interval '1 month'

    UNION ALL

    SELECT employee_id, a.child_id, role
    FROM placement_plan pp
    JOIN application a ON pp.application_id = a.id
    JOIN daycare_acl_view acl ON pp.unit_id = acl.daycare_id
    WHERE a.status = ANY ('{SENT,WAITING_PLACEMENT,WAITING_CONFIRMATION,WAITING_DECISION,WAITING_MAILING,WAITING_UNIT_CONFIRMATION, ACTIVE}'::application_status_type[])

    UNION ALL

    SELECT employee_id, child_id, 'GROUP_STAFF'
    FROM daycare_group_placement dgp
    JOIN placement dp ON dp.id = dgp.daycare_placement_id
    JOIN daycare_group_acl USING (daycare_group_id)
    WHERE daterange(dgp.start_date, dgp.end_date, '[]') @> current_date

    UNION ALL

    SELECT
        employee_id, child_id,
        (CASE
            WHEN role = 'UNIT_SUPERVISOR' THEN 'UNIT_SUPERVISOR'
            ELSE 'STAFF'
        END)::user_role
    FROM backup_care bc
    JOIN daycare_acl_view acl ON acl.daycare_id = bc.unit_id
    WHERE bc.end_date > current_date - INTERVAL '1 month'

    UNION ALL

    SELECT employee_id, child_id, 'GROUP_STAFF'
    FROM backup_care bc
    JOIN daycare_group_acl acl ON bc.group_id = acl.daycare_group_id
    WHERE bc.end_date > current_date - interval '1 month'
);

CREATE VIEW person_acl_view(employee_id, person_id, role) AS (
    SELECT *
    FROM child_acl_view

    UNION ALL

    SELECT acl.employee_id, guardian_id AS person_id, acl.role
    FROM child_acl_view acl
    JOIN guardian
    USING (child_id)

    UNION ALL

    SELECT acl.employee_id, head_of_child AS person_id, acl.role
    FROM child_acl_view acl
    JOIN fridge_child
    USING (child_id)
);
