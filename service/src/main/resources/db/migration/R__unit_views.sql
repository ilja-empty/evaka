CREATE OR REPLACE VIEW location_view (
                                      id,
                                      name,
                                      type,
                                      care_area_id,
                                      phone,
                                      street_address,
                                      postal_code,
                                      post_office,
                                      mailing_street_address,
                                      mailing_po_box,
                                      mailing_postal_code,
                                      mailing_post_office,
                                      location,
                                      url,
                                      provider_type,
                                      language,
                                      daycare_apply_period,
                                      preschool_apply_period,
                                      club_apply_period
    ) AS
    SELECT daycare.id,
           daycare.name,
           daycare.type,
           daycare.care_area_id,
           daycare.phone,
           daycare.street_address,
           daycare.postal_code,
           daycare.post_office,
           daycare.mailing_street_address,
           daycare.mailing_po_box,
           daycare.mailing_postal_code,
           daycare.mailing_post_office,
           daycare.location,
           daycare.url,
           daycare.provider_type,
           daycare.language,
           daycare.daycare_apply_period,
           daycare.preschool_apply_period,
           daycare.club_apply_period
    FROM daycare;
