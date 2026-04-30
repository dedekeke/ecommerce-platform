import { bootstrap } from './bootstrap';

describe('bootstrap function', () => {
  it('should export a bootstrap function', () => {
    expect(typeof bootstrap).toBe('function');
  });

  it('should throw when the mount element is not found in the DOM', async () => {
    await expectAsync(
      bootstrap('non-existent-element-id-xyz')
    ).toBeRejectedWithError(/non-existent-element-id-xyz/);
  });

  it('should accept an elementId parameter', () => {
    const params = bootstrap.length;
    expect(params).toBe(1);
  });
});
