package dev.sablelife.compat.littletiles;

/** Implemented only by the verified, optional client cache mixin. */
public interface LostMesh {
    void sablelife$lostMesh();
    void sablelife$builtMesh();
    boolean sablelife$hasLoss();
    boolean sablelife$takeRecovery(long now);
}
