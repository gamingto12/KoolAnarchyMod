package eu.koolfreedom.staff;

public enum StaffRole
{
    OWNER,
    CO_OWNER,
    STAFF;

    public static StaffRole fromString(String value)
    {
        if (value == null) return STAFF;
        try
        {
            return valueOf(value.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            return STAFF;
        }
    }
}

