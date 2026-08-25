// EXPECT: react/forbid-dom-props
// All custom styling lives in styled-components. The only sanctioned inline style is the
// zoom/pan transform in TreeCanvas (doc 07 §7.3), which carries an explicit disable comment.
export function Banner() {
  return <div style={{ color: 'red' }}>Hard-coded styling</div>;
}
