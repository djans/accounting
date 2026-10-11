# Company user access and roles

Administrators manage access to the selected company at **Administration /
Company users** (`/admin/users`). A login account can belong to multiple
companies; each row in `user_company_memberships` stores that user's access and
role for one company. Granting, changing, or revoking one membership does not
change the user's access to other companies or delete the global login account.
Company roles are reloaded for the selected company on every request.

The available roles are:

- **Administrator** — manages company users and company settings; can perform
  all application operations.
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
