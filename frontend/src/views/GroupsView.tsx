import { useState, type FormEvent } from 'react'
import type { Group, Member } from '../api/types'
import { EmptyState, SectionHeading } from '../components/ui'

export function GroupsView({
  groups,
  selectedGroupId,
  members,
  busy,
  onSelectGroup,
  onCreateGroup,
  onAddMember,
}: {
  groups: Group[]
  selectedGroupId: string
  members: Member[]
  busy: string
  onSelectGroup: (id: string) => void
  onCreateGroup: (name: string) => Promise<boolean>
  onAddMember: (name: string, email: string) => Promise<boolean>
}) {
  const [groupName, setGroupName] = useState('')
  const [memberName, setMemberName] = useState('')
  const [memberEmail, setMemberEmail] = useState('')
  const [formError, setFormError] = useState('')

  async function submitGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!groupName.trim()) return setFormError('Enter a group name.')
    setFormError('')
    if (await onCreateGroup(groupName.trim())) setGroupName('')
  }

  async function submitMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!memberName.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(memberEmail.trim())) {
      return setFormError('Enter a member name and a valid email address.')
    }
    setFormError('')
    if (await onAddMember(memberName.trim(), memberEmail.trim())) {
      setMemberName('')
      setMemberEmail('')
    }
  }

  return (
    <section className="view-stack">
      <SectionHeading eyebrow="PEOPLE & PLANS" title="Groups and members" />
      <div className="two-column">
        <div className="panel">
          <h3>Your groups</h3>
          {groups.length === 0
            ? <EmptyState>No group memberships yet. Create your first group.</EmptyState>
            : <ul className="group-list">
              {groups.map((group) => (
                <li key={group.id}>
                  <button
                    className={`group-choice ${group.id === selectedGroupId ? 'selected' : ''}`}
                    onClick={() => onSelectGroup(group.id)}
                    aria-pressed={group.id === selectedGroupId}
                  >
                    <span>{group.name}</span><small>Created {new Date(group.createdAt).toLocaleDateString()}</small>
                  </button>
                </li>
              ))}
            </ul>}
          <form className="form-stack separated" onSubmit={submitGroup}>
            <label htmlFor="new-group-name">Create a group</label>
            <div className="inline-form">
              <input id="new-group-name" value={groupName} onChange={(event) => setGroupName(event.target.value)} maxLength={120} required />
              <button className="button button-primary" disabled={busy !== ''}>{busy === 'create-group' ? 'Creating…' : 'Create group'}</button>
            </div>
          </form>
        </div>
        <div className="panel">
          <h3>Members</h3>
          {!selectedGroupId
            ? <EmptyState>Select or create a group to see its members.</EmptyState>
            : members.length === 0
              ? <EmptyState>No member records are available for this group.</EmptyState>
              : <ul className="data-list">
                {members.map((member) => (
                  <li key={member.id}>
                    <div><strong>{member.name}</strong><small>{member.email}</small></div>
                    <div className="member-meta"><span>{member.role}</span><span className={`membership-${member.status.toLowerCase()}`}>{member.status}</span></div>
                  </li>
                ))}
              </ul>}
          <form className="form-stack separated" onSubmit={submitMember}>
            <label htmlFor="member-name">Add a member</label>
            <div className="form-row">
              <input id="member-name" placeholder="Name" value={memberName} onChange={(event) => setMemberName(event.target.value)} maxLength={100} required />
              <input aria-label="Member email" type="email" placeholder="Email" value={memberEmail} onChange={(event) => setMemberEmail(event.target.value)} maxLength={254} required />
            </div>
            <button className="button button-secondary" disabled={!selectedGroupId || busy !== ''}>{busy === 'add-member' ? 'Adding…' : 'Add member'}</button>
            <small className="note">The backend creates or reuses a member record. Authentication remains development-only.</small>
          </form>
        </div>
      </div>
      {formError && <p role="alert" className="error-message">{formError}</p>}
    </section>
  )
}
