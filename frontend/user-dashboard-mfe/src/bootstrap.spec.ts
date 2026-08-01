import { bootstrap } from './bootstrap';

describe('bootstrap function', () => {
  it('should export a bootstrap function', () => {
    expect(typeof bootstrap).toBe('function');
  });

  it('should accept an elementId parameter', () => {
    expect(bootstrap.length).toBe(1);
  });

  it('should throw when the mount element is not found in the DOM', async () => {
    await expectAsync(
      bootstrap('non-existent-element-id-xyz')
    ).toBeRejectedWithError(/non-existent-element-id-xyz/);
  });

  it('should return a destroy handle (function) on successful bootstrap', async () => {
    const el = document.createElement('div');
    el.id = 'test-mount-user';
    document.body.appendChild(el);

    try {
      const destroy = await bootstrap('test-mount-user');
      expect(typeof destroy).toBe('function');
      destroy();
    } finally {
      // destroy() may already have detached the mount node; remove defensively.
      if (el.parentNode) {
        el.parentNode.removeChild(el);
      }
    }
  });
});
