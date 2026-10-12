# Company user access and roles

Administrators manage access to the selected company at **Administration /
Company users** (`/admin/users`). A login account can belong to multiple
companies; each row in `user_company_memberships` stores that user's access and
role for one company. Granting, changing, or revoking one membership does not
change the user's access to other companies or delete the global login account.
Company roles are reloaded for the selected company on every request.

Administrators can edit the selected company's profile from **Companies**.
Profile changes update the company record identified by its database ID; they
do not reassign or rewrite company-owned accounting records. Newly generated
invoice PDFs use the updated company details, so preserve an issued PDF or add
invoice-level detail snapshots if historical document appearance must remain
unchanged.

## Company administrators and SuperAdmins

Each company administrator is authorized by that company's membership only.
Company administrators can manage that company's users and profile, view the
database schema, and reset that company's accounting and reconciliation
activity. They cannot create or administer other companies, inspect system
monitoring or SQL tools, migrate the database engine, apply global schema
migrations, or restart the entire database.

SuperAdmins are configured outside company memberships with the comma-separated
`APP_SUPER_ADMIN_EMAILS` environment variable. Matching is case-insensitive.
A SuperAdmin can select and administer every company and use global database
operations. In the local profile, `root@example.com` is the default SuperAdmin;
development, staging, and production have no default SuperAdmin and must set
the environment variable explicitly. Existing users become SuperAdmins only
when their email is configured there; company administrators cannot grant this
privilege from **Company users**. The configured account must already exist and
be enabled, and the configuration takes effect after the application restarts.
For a new or reset database, include the bootstrap administrator's email if that
account must perform global operations.

The **Database Backup** page is available to company administrators, but its
current JSON backup includes every company's data and restore replaces the
entire database. Therefore export and restore are limited to SuperAdmins;
company-scoped backup files are not currently supported. Company administrators
can view database schema status, while applying migrations and restarting the
database are global SuperAdmin operations. The **Reset** page exposes only
selected-company activity resets to company administrators.

The available roles are:

- **Administrator** — manages users and settings for the company membership
  where this role is assigned and can perform that company's application
  operations.
- **SuperAdmin** — a deployment-configured global operator, not a company
  membership role; can administer every company and use global database tools.
- **Accountant** — can create and change accounting, billing, and tax records.
- **Bookkeeper** — can create and change day-to-day accounting and billing records.
- **Payment operator** — can record customer payments, pay bills, issue or void
  cheques, and record tax payments; cannot change other accounting records.
- **Auditor** — read-only access.

Accountant and Bookkeeper currently have the same application write permissions;
the distinction is available for future refinement. Payment-operator permissions
are limited to payment-oriented endpoints. Payment release workflows should
eventually support separation of duties, such as preparation by one user and
approval by another.

An administrator can create a login account and assign its role in the current
company, or grant an existing enabled account access with a selected role.
New accounts require a password of at least 12 characters, containing at least
one letter, one number, and one special character. Passwords are hashed and
never logged or emailed. An administrator cannot remove their own membership,
demote their own administrator role, or remove/demote the last enabled
administrator for a company.

## Existing installations

Schema version `1.3.0` adds the role column to company memberships and a unique
seven-character ShortID to each account. During migration, every existing
membership receives that account's former global role; legacy `VIEWER` roles
become `AUDITOR`. Thus existing users retain their previous access in each
company they already belonged to. The old `app_users.role` value is retained
only for compatibility with existing database layouts and is no longer used
for authorization.

ShortIDs are generated from the user's family name and given name, followed by
a three-digit sequence beginning at `001`. To honor the requested seven
characters while retaining three sequence digits, the identifier uses the
first three family-name letters, the first given-name letter, and the sequence
(for example, `DOEJ001` for Jane Doe). If the family name is shorter than three letters,
given-name letters fill the prefix. ShortIDs are globally unique and are
assigned to existing accounts as part of migration.

User-activity log actor fields use ShortIDs instead of email addresses after
schema version `1.3.0` is applied. Before that migration, actor values can still
be email addresses.
