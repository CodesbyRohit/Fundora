/**
 * Module V: React Integration & SPA Component Design
 * Component: FundoraSavingsTracker & LiveBalanceCalculator
 */

const { useState, useEffect } = React;

function SavingsTracker({ initialGroupId }) {
    const [goals, setGoals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [contributionAmounts, setContributionAmounts] = useState({});
    const [newGoalTitle, setNewGoalTitle] = useState('');
    const [newGoalTarget, setNewGoalTarget] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        fetchGoals();
    }, [initialGroupId]);

    // Surfaces the {code, message} body returned by the REST API
    const readError = async (res, fallback) => {
        try {
            const body = await res.json();
            return (body && body.message) || fallback;
        } catch (_) {
            return fallback;
        }
    };

    const fetchGoals = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/groups/${initialGroupId}/savings`);
            if (res.ok) {
                setGoals(await res.json());
                setError('');
            } else {
                setError(await readError(res, 'Could not load savings goals.'));
            }
        } catch (err) {
            console.error("Failed to load savings goals:", err);
            setError('Network error while loading savings goals.');
        } finally {
            setLoading(false);
        }
    };

    const handleContribute = async (goalId) => {
        const amount = contributionAmounts[goalId];
        if (!amount || !(parseFloat(amount) > 0)) {
            setError("Please enter a valid contribution amount.");
            return;
        }

        setBusy(true);
        try {
            const res = await fetch(`/api/savings/${goalId}/contribute`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: amount })
            });

            if (res.ok) {
                setContributionAmounts(prev => ({ ...prev, [goalId]: '' }));
                await fetchGoals();
            } else {
                setError(await readError(res, 'Contribution failed.'));
            }
        } catch (err) {
            console.error("Contribution error:", err);
            setError('Network error while contributing.');
        } finally {
            setBusy(false);
        }
    };

    const handleCreateGoal = async (e) => {
        e.preventDefault();
        if (!newGoalTitle.trim() || !(parseFloat(newGoalTarget) > 0)) {
            setError('Enter a goal title and a target amount greater than zero.');
            return;
        }

        setBusy(true);
        try {
            const res = await fetch(`/api/groups/${initialGroupId}/savings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title: newGoalTitle.trim(),
                    targetAmount: newGoalTarget,
                    deadline: new Date(Date.now() + 60*24*60*60*1000).toISOString().split('T')[0]
                })
            });

            if (res.ok) {
                setNewGoalTitle('');
                setNewGoalTarget('');
                await fetchGoals();
            } else {
                setError(await readError(res, 'Could not create the goal.'));
            }
        } catch (err) {
            console.error("Create goal error:", err);
            setError('Network error while creating the goal.');
        } finally {
            setBusy(false);
        }
    };

    if (loading && goals.length === 0) {
        return <div style={{ color: '#94a3b8' }}>Loading collaborative savings goals...</div>;
    }

    const formatINR = (n) => Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 });

    return (
        <div>
            {error && (
                <div className="alert-box alert-danger" role="alert" style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>⚠️ {error}</span>
                    <button onClick={() => setError('')} aria-label="Dismiss" style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}>×</button>
                </div>
            )}

            {goals.length === 0 && (
                <p style={{ color: '#94a3b8', fontSize: '0.9rem' }}>No savings goals for this group yet.</p>
            )}

            {goals.map(goal => {
                const pct = goal.targetAmount > 0
                    ? Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100))
                    : 0;
                return (
                    <div key={goal.id} style={{
                        background: '#334155',
                        padding: '1.25rem',
                        borderRadius: '10px',
                        marginBottom: '1rem',
                        border: '1px solid #475569'
                    }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                            <strong style={{ color: '#f8fafc', fontSize: '1.05rem' }}>{goal.title}</strong>
                            <span style={{ 
                                background: goal.status === 'COMPLETED' ? '#10b981' : '#06b6d4', 
                                color: '#fff', 
                                padding: '0.15rem 0.5rem', 
                                borderRadius: '6px',
                                fontSize: '0.75rem',
                                fontWeight: 'bold'
                            }}>
                                {goal.status}
                            </span>
                        </div>

                        {/* Progress Bar */}
                        <div style={{ 
                            background: '#1e293b', 
                            borderRadius: '9999px', 
                            height: '14px', 
                            overflow: 'hidden', 
                            marginBottom: '0.75rem' 
                        }}>
                            <div style={{
                                width: `${pct}%`,
                                background: 'linear-gradient(90deg, #4f46e5, #06b6d4)',
                                height: '100%',
                                transition: 'width 0.4s ease-in-out'
                            }}></div>
                        </div>

                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', color: '#94a3b8', marginBottom: '0.75rem' }}>
                            <span>Saved: <strong style={{ color: '#10b981' }}>₹{formatINR(goal.currentAmount)}</strong></span>
                            <span>Target: ₹{formatINR(goal.targetAmount)} ({pct}%)</span>
                        </div>

                        {goal.status === 'ACTIVE' && (
                            <div style={{ display: 'flex', gap: '0.5rem' }}>
                                <input
                                    type="number"
                                    min="0.01"
                                    step="0.01"
                                    placeholder="Contribute ₹"
                                    value={contributionAmounts[goal.id] || ''}
                                    onChange={(e) => setContributionAmounts({ ...contributionAmounts, [goal.id]: e.target.value })}
                                    style={{
                                        flex: 1,
                                        padding: '0.45rem 0.75rem',
                                        background: '#0f172a',
                                        border: '1px solid #475569',
                                        borderRadius: '6px',
                                        color: '#fff'
                                    }}
                                />
                                <button
                                    onClick={() => handleContribute(goal.id)}
                                    disabled={busy}
                                    style={{
                                        background: '#10b981',
                                        color: '#fff',
                                        border: 'none',
                                        borderRadius: '6px',
                                        padding: '0.45rem 0.9rem',
                                        cursor: 'pointer',
                                        fontWeight: '600'
                                    }}
                                >
                                    + Add
                                </button>
                            </div>
                        )}
                    </div>
                );
            })}

            {/* Quick Add Goal Form */}
            <form onSubmit={handleCreateGoal} style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px dashed #475569' }}>
                <h4 style={{ color: '#f8fafc', marginBottom: '0.75rem', fontSize: '0.95rem' }}>🎯 Create New Trip / Event Savings Goal</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    <input
                        type="text"
                        placeholder="Goal title (e.g. Goa Scuba Diving / Smart TV)"
                        maxLength={150}
                        value={newGoalTitle}
                        onChange={(e) => setNewGoalTitle(e.target.value)}
                        required
                        style={{
                            padding: '0.5rem 0.75rem',
                            background: '#0f172a',
                            border: '1px solid #475569',
                            borderRadius: '6px',
                            color: '#fff'
                        }}
                    />
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            placeholder="Target ₹ (e.g. 15000)"
                            value={newGoalTarget}
                            onChange={(e) => setNewGoalTarget(e.target.value)}
                            required
                            style={{
                                flex: 1,
                                padding: '0.5rem 0.75rem',
                                background: '#0f172a',
                                border: '1px solid #475569',
                                borderRadius: '6px',
                                color: '#fff'
                            }}
                        />
                        <button
                            type="submit"
                            disabled={busy}
                            style={{
                                background: '#4f46e5',
                                color: '#fff',
                                border: 'none',
                                borderRadius: '6px',
                                padding: '0.5rem 1.2rem',
                                cursor: 'pointer',
                                fontWeight: '600'
                            }}
                        >
                            Create Goal
                        </button>
                    </div>
                </div>
            </form>
        </div>
    );
}

// Mount React Component to DOM container
const rootElement = document.getElementById('reactSavingsTrackerRoot');
if (rootElement) {
    const groupId = rootElement.getAttribute('data-group-id');
    if (groupId) ReactDOM.createRoot(rootElement).render(<SavingsTracker initialGroupId={groupId} />);
}
